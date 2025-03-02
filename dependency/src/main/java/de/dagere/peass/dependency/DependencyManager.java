/**
 *     This file is part of PerAn.
 *
 *     PerAn is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     PerAn is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with PerAn.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.dagere.peass.dependency;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.KiekerConfig;
import de.dagere.peass.dependency.analysis.CalledMethodLoader;
import de.dagere.peass.dependency.analysis.ModuleClassMapping;
import de.dagere.peass.dependency.analysis.data.CalledMethods;
import de.dagere.peass.dependency.analysis.data.ChangedEntity;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.analysis.data.TestDependencies;
import de.dagere.peass.dependency.analysis.data.TestExistenceChanges;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.analysis.testData.TestClazzCall;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependency.traces.KiekerFolderUtil;
import de.dagere.peass.execution.maven.pom.MavenPomUtil;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.execution.utils.TestExecutor;
import de.dagere.peass.folders.PeassFolders;

/**
 * Runs tests with kieker and reads the dependencies of tests for each version
 * 
 * @author reichelt
 *
 */
public class DependencyManager extends KiekerResultManager {
   
   private static final Logger LOG = LogManager.getLogger(DependencyManager.class);

   private final TestDependencies dependencies = new TestDependencies();
   private long deleteFolderSize;

   /**
    * Creates a new ChangeTestClassesHandler for the given folder with the given groupId and projectId. The groupId and projectId are needed to determine where the results are
    * afterwards.
    * 
    * @param projectFolder
    * @throws SecurityException
    * @throws NoSuchMethodException
    * @throws InvocationTargetException
    * @throws IllegalArgumentException
    * @throws IllegalAccessException
    * @throws InstantiationException
    * @throws ClassNotFoundException
    */
   public DependencyManager(final PeassFolders folders, final ExecutionConfig executionConfig, final KiekerConfig kiekerConfig, final EnvironmentVariables env) {
      super(folders, executionConfig, kiekerConfig, env);
      deleteFolderSize = kiekerConfig.getTraceSizeInMb() * 10;
   }

   public DependencyManager(final TestExecutor executor, final PeassFolders folders) {
      super(executor, folders);
   }

   public TestDependencies getDependencyMap() {
      return dependencies;
   }

   public void setDeleteFolderSize(final int deleteFolderSize) {
      this.deleteFolderSize = deleteFolderSize;
   }

   public boolean initialyGetTraces(final String commit) throws IOException {
      if (folders.getTempMeasurementFolder().exists()) {
         FileUtils.deleteDirectory(folders.getTempMeasurementFolder());
      }

      final ModuleClassMapping mapping = new ModuleClassMapping(executor);
      executor.loadClasses();

      TestSet tests = findIncludedTests(mapping);
      if (tests.getTestMethods().isEmpty()) {
         LOG.error("No tests were selected - maybe the tests are all disabled or no tests meets the pattern");
         return false;
      }
      runTraceTests(tests, commit);

      if (folders.getTempMeasurementFolder().exists()) {
         return readInitialResultFiles(mapping);
      } else {
         return printErrors();
      }
   }

   private TestSet findIncludedTests(final ModuleClassMapping mapping) throws IOException {
      List<String> includedModules = getIncludedModules();

      return testTransformer.findModuleTests(mapping, includedModules, executor.getModules());
   }

   private List<String> getIncludedModules() throws IOException {
      List<String> includedModules;
      if (testTransformer.getConfig().getExecutionConfig().getPl() != null) {
         includedModules = MavenPomUtil.getDependentModules(folders.getProjectFolder(), testTransformer.getConfig().getExecutionConfig().getPl(), executor.getEnv());
         LOG.debug("Included modules: {}", includedModules);
      } else {
         includedModules = null;
      }
      return includedModules;
   }

   private boolean printErrors() throws IOException {
      try {
         boolean sourceFound = false;
         sourceFound = searchTestFiles(sourceFound);
         if (sourceFound) {
            LOG.debug("No result data available - error occured?");
            return false;
         } else {
            LOG.debug("No result data available, but no test-classes existing - so it is ok.");
            return true;
         }
      } catch (final XmlPullParserException e) {
         e.printStackTrace();
         return false;
      }
   }

   private boolean searchTestFiles(boolean sourceFound) throws IOException, XmlPullParserException {
      for (final File module : executor.getModules().getModules()) {
         final File testSourceFolder = new File(module, "src" + File.separator + "test");
         if (testSourceFolder.exists()) {
            final Collection<File> javaTestFiles = FileUtils.listFiles(testSourceFolder, new WildcardFileFilter("*test*.java", IOCase.INSENSITIVE), TrueFileFilter.INSTANCE);
            if (javaTestFiles.size() > 0) {
               sourceFound = true;
            }
         }
      }
      return sourceFound;
   }

   private boolean readInitialResultFiles(final ModuleClassMapping mapping) {
      final Collection<File> xmlFiles = FileUtils.listFiles(folders.getTempMeasurementFolder(), new WildcardFileFilter("*.json"), TrueFileFilter.INSTANCE);
      LOG.debug("Initial test execution finished, starting result collection, analyzing {} files", xmlFiles.size());
      for (final File testResultFile : xmlFiles) {
         final File parent = testResultFile.getParentFile();
         final String testClassName = parent.getName();
         final String testMethodName = testResultFile.getName().substring(0, testResultFile.getName().length() - ".json".length()); // remove
         // .xml
         final String moduleOfClass = mapping.getModuleOfClass(testClassName);
         if (moduleOfClass == null) {
            throw new RuntimeException("Module of class " + testClassName + " is null");
         }
         TestMethodCall testcase = new TestMethodCall(testClassName, testMethodName, moduleOfClass);
         updateDependenciesOnce(testcase, parent, mapping);
      }
      LOG.debug("Result collection finished");
      return true;
   }

   public void cleanResultFolder() {
      final File movedInitialResults = new File(folders.getTempMeasurementFolder().getParentFile(), "initialresults_kieker");
      if (movedInitialResults.exists()) {
         try {
            LOG.info("Deleting old initialresults");
            FileUtils.deleteDirectory(movedInitialResults);
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      try {
         FileUtils.moveDirectory(folders.getTempMeasurementFolder(), movedInitialResults);
      } catch (IOException e) {
         LOG.info("Moving did not succeed");
         LOG.info("Error: " + e.getLocalizedMessage());
         e.printStackTrace();
      }
      if (movedInitialResults.exists()) {
         for (File classFolder : movedInitialResults.listFiles()) {
            LOG.debug("Cleaning {}", classFolder.getAbsolutePath());
            if (classFolder.isDirectory()) {
               cleanFolderAboveSize(classFolder, deleteFolderSize);
            }
         }
      } else {
         LOG.error("Initial result folder did not exist");
      }
   }

   /**
    * Updates Dependencies of the given testClassName and the given testMethodName based upon the file where the kieker-results are stored
    * 
    * @param testClassName
    * @param testMethodName
    * @param parent
    */
   public void updateDependenciesOnce(final TestMethodCall testcase, final File parent, final ModuleClassMapping mapping) {
      LOG.debug("Parent: " + parent);
      final File moduleResultsFolder = KiekerFolderUtil.getModuleResultFolder(folders, testcase);
      final File[] kiekerResultFolders = KiekerFolderUtil.getClazzMethodFolder(testcase, moduleResultsFolder);

      if (kiekerResultFolders == null) {
         LOG.error("No kieker folder found: " + parent);
         return;
      }

      final Map<ChangedEntity, Set<String>> allCalledClasses = getCalledMethods(mapping, kiekerResultFolders);

      removeNotExistingClazzes(allCalledClasses);
      for (final Iterator<ChangedEntity> iterator = allCalledClasses.keySet().iterator(); iterator.hasNext();) {
         final ChangedEntity clazz = iterator.next();
         if (clazz.getModule() == null) {
            throw new RuntimeException("Class " + clazz.getJavaClazzName() + " has no module!");
         }
      }

      LOG.debug("Test: {} ", testcase);
      LOG.debug("Dependencies: {}", allCalledClasses.size());
      
      boolean testExcluded = false;

      if (forbiddenMethodCalled(allCalledClasses)) {
         testExcluded = true;
         testTransformer.getConfig().getExecutionConfig().getExcludes().add(testcase.getExecutable());
      }
      
      if (getTraceSize(kiekerResultFolders) > testTransformer.getConfig().getKiekerConfig().getTraceSizeInMb()) {
         testExcluded = true;
      }

      if (!testExcluded) {
         dependencies.setDependencies(testcase, allCalledClasses);
      }
   }

   private boolean forbiddenMethodCalled(final Map<ChangedEntity, Set<String>> allCalledClasses) {
      List<String> forbiddenMethods = testTransformer.getConfig().getExecutionConfig().getForbiddenMethods();

      for (String forbiddenMethod : forbiddenMethods) {
         if (traceContainsMethod(allCalledClasses, forbiddenMethod)) {
            return true;
         }
      }

      return false;
   }

   private boolean traceContainsMethod(final Map<ChangedEntity, Set<String>> allCalledClasses, final String methodToFind) {
      String[] classAndMethod = methodToFind.split("#");

      for (Entry<ChangedEntity, Set<String>> calledClass : allCalledClasses.entrySet()) {
         if (calledClass.getKey().getClazz().equals(classAndMethod[0])) {
            Set<String> calledMethods = calledClass.getValue();
            if (calledMethods.contains(classAndMethod[1])) {
               return true;
            }
         }
      }

      return false;
   }

   private long getTraceSize(final File[] kiekerResultFolders) {
      long overallSizeInMB = 0;
      for (File kiekerResultFolder : kiekerResultFolders) {
         final long size = FileUtils.sizeOfDirectory(kiekerResultFolder);
         final long sizeInMB = size / (1024 * 1024);
         overallSizeInMB += sizeInMB;

         LOG.debug("Size: {} Folder: {}", sizeInMB, kiekerResultFolder);
      }

      return overallSizeInMB;
   }

   private Map<ChangedEntity, Set<String>> getCalledMethods(final ModuleClassMapping mapping, final File[] kiekerResultFolders) {
      final Map<ChangedEntity, Set<String>> allCalledClasses = new LinkedHashMap<ChangedEntity, Set<String>>();
      for (File kiekerResultFolder : kiekerResultFolders) {
         LOG.debug("Reading Kieker folder: {}", kiekerResultFolder.getAbsolutePath());
         
         CalledMethodLoader calledMethodLoader = new CalledMethodLoader(kiekerResultFolder, mapping, testTransformer.getConfig().getKiekerConfig());
         final Map<ChangedEntity, Set<String>> calledMethods = calledMethodLoader.getCalledMethods();
         for (Map.Entry<ChangedEntity, Set<String>> calledMethod : calledMethods.entrySet()) {
            if (!allCalledClasses.containsKey(calledMethod.getKey())) {
               allCalledClasses.put(calledMethod.getKey(), calledMethod.getValue());
            } else {
               Set<String> alreadyKnownCalledClasses = allCalledClasses.get(calledMethod.getKey());
               alreadyKnownCalledClasses.addAll(calledMethod.getValue());
            }
         }
      }
      return allCalledClasses;
   }

   private void removeNotExistingClazzes(final Map<ChangedEntity, Set<String>> calledClasses) {
      for (final Iterator<ChangedEntity> iterator = calledClasses.keySet().iterator(); iterator.hasNext();) {
         final ChangedEntity entity = iterator.next();
         final String wholeClassName = entity.getJavaClazzName();

         // ignore inner class part, because it is in the same file - if the file exists, it is very likely that a subclass, which is in the logs, exists also
         final String outerClazzName = ClazzFileFinder.getOuterClass(wholeClassName);
         LOG.trace("Testing: " + outerClazzName);
         if (!executor.getExistingClasses().contains(outerClazzName)) {
            // Removes classes not in package
            iterator.remove();
         } else {
            LOG.trace("Existing: " + outerClazzName);
         }
      }
   }

   /**
    * Updates the dependencies of the current version by running each testclass once. The testcases, that have been added in this version, are returned (since they can not be
    * determined from the old dependency information or the svn diff directly). TODO: What if testcases are removed?
    * 
    * @param testsToUpdate
    * @return
    */
   public TestExistenceChanges updateDependencies(final TestSet testsToUpdate, final ModuleClassMapping mapping) {
      final Map<TestMethodCall, Map<ChangedEntity, Set<String>>> oldDepdendencies = dependencies.getCopiedDependencies();

      // Remove all old dependencies where changes happened, because they may
      // have been deleted
      for (final Entry<TestClazzCall, Set<String>> className : testsToUpdate.entrySet()) {
         for (final String method : className.getValue()) {
            final TestMethodCall entity = new TestMethodCall(className.getKey().getClazz(), method, className.getKey().getModule());
            dependencies.getDependencyMap().remove(entity);
         }
      }

      LOG.debug("Beginne Abhängigkeiten-Aktuallisierung für {} Klassen", testsToUpdate.getClasses().size());

      final TestExistenceChanges changes = populateExistingTests(testsToUpdate, mapping, oldDepdendencies);

      findAddedTests(oldDepdendencies, changes);
      return changes;
   }

   private TestExistenceChanges populateExistingTests(final TestSet testsToUpdate, final ModuleClassMapping mapping,
         final Map<TestMethodCall, Map<ChangedEntity, Set<String>>> oldDepdendencies) {
      final TestExistenceChanges changes = new TestExistenceChanges();

      for (final Entry<TestClazzCall, Set<String>> entry : testsToUpdate.entrySet()) {
         final File testclazzFolder = getTestclazzFolder(entry);
         LOG.debug("Suche in {} Existiert: {} Ordner: {} Tests: {} ", testclazzFolder.getAbsolutePath(), testclazzFolder.exists(), testclazzFolder.isDirectory(), entry.getValue());
         if (testclazzFolder.exists()) {
            updateMethods(mapping, changes, entry, testclazzFolder);
         } else {
            checkRemoved(oldDepdendencies, changes, entry, testclazzFolder);
         }
      }
      return changes;
   }

   public File getTestclazzFolder(final Entry<TestClazzCall, Set<String>> entry) {
      File moduleResultFolder = KiekerFolderUtil.getModuleResultFolder(folders, entry.getKey());
      File testclazzFolder = new File(moduleResultFolder, entry.getKey().getClazz());
      return testclazzFolder;
   }

   void updateMethods(final ModuleClassMapping mapping, final TestExistenceChanges changes, final Entry<TestClazzCall, Set<String>> entry, final File testclazzFolder) {
      final Set<String> notFound = new TreeSet<>();
      notFound.addAll(entry.getValue());
      for (final File testResultFile : testclazzFolder.listFiles((FileFilter) new WildcardFileFilter("*.json"))) {
         final String testClassName2 = testResultFile.getParentFile().getName();
         if (!testClassName2.equals(entry.getKey().getClazz())) {
            LOG.error("Testclass " + entry.getKey().getClazz() + " != " + testClassName2);
         }
         final File parent = testResultFile.getParentFile();
         final String testMethodName = testResultFile.getName().substring(0, testResultFile.getName().length() - ".json".length());
         final String module = mapping.getModuleOfClass(entry.getKey().getClazz());
         updateDependenciesOnce(new TestMethodCall(entry.getKey().getClazz(), testMethodName, module), parent, mapping);
         notFound.remove(testMethodName);
      }
      LOG.debug("Removed tests: {}", notFound);
      for (final String testMethodName : notFound) {
         final TestCase entity = new TestMethodCall(entry.getKey().getClazz(), testMethodName, entry.getKey().getModule());
         dependencies.removeTest(entity);
         // testsToUpdate.removeTest(entry.getKey(), testMethodName);
         changes.addRemovedTest(new TestMethodCall(entry.getKey().getClazz(), testMethodName, entry.getKey().getModule()));
      }
   }

   void checkRemoved(final Map<TestMethodCall, Map<ChangedEntity, Set<String>>> oldDepdendencies, final TestExistenceChanges changes, final Entry<TestClazzCall, Set<String>> entry,
         final File testclazzFolder) {
      LOG.error("Testclass {} does not exist anymore or does not create results. Folder: {}", entry.getKey(), testclazzFolder);
      final TestClazzCall testclass = new TestClazzCall(entry.getKey().getClazz(), entry.getKey().getModule());
      boolean oldContained = false;
      for (final TestMethodCall oldTest : oldDepdendencies.keySet()) {
         if (testclass.getClazz().equals(oldTest.getClazz()) && testclass.getModule().equals(oldTest.getModule())) {
            oldContained = true;
         }
      }
      if (oldContained) {
         changes.addRemovedTest(testclass);
      } else {
         LOG.trace("Test was only added incorrect (was not measured before), is not counted as removed test.");
      }
   }

   /**
    * A method is unknown if a class-wide change happened, e.g. if a new subclass is declared and because of this change a new testcase needs to be called.
    * 
    * @param oldDepdendencies
    * @param changes
    */
   private void findAddedTests(final Map<TestMethodCall, Map<ChangedEntity, Set<String>>> oldDepdendencies, final TestExistenceChanges changes) {
      for (final Map.Entry<TestMethodCall, CalledMethods> newDependency : dependencies.getDependencyMap().entrySet()) {
         // testclass -> depending class -> method
         final TestMethodCall testcase = newDependency.getKey();
         if (!oldDepdendencies.containsKey(testcase)) {
            changes.addAddedTest(testcase.onlyClazzEntity(), testcase);
            for (final Map.Entry<ChangedEntity, Set<String>> newCallees : newDependency.getValue().getCalledMethods().entrySet()) {
               final ChangedEntity changedclass = newCallees.getKey();
               for (final String changedMethod : newCallees.getValue()) {
                  // Since the testcase is new, is is always caused
                  // primarily by a change of the test class, and not of
                  // any other changed class
                  final ChangedEntity methodEntity = changedclass.copy();
                  methodEntity.setMethod(changedMethod);
                  changes.addAddedTest(methodEntity, testcase);
               }
            }
         }
      }
   }

   public void addDependencies(final TestMethodCall test, final Map<ChangedEntity, Set<String>> calledClasses) {
      dependencies.addDependencies(test, calledClasses);
   }
}
