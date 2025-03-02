package de.dagere.peass.config.parameters;

import java.util.List;

import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.WorkloadType;
import picocli.CommandLine.Option;

public class ExecutionConfigMixin {
   public final static String CLAZZ_FOLDERS_DEFAULT = ExecutionConfig.SRC_MAIN_JAVA + ":" + ExecutionConfig.SRC_JAVA;
   public final static String TEST_FOLDERS_DEFAULT = ExecutionConfig.SRC_TEST_JAVA + ":" + ExecutionConfig.SRC_TEST + ":" + ExecutionConfig.SRC_ANDROID_TEST_JAVA;

   @Option(names = { "-timeout", "--timeout" }, description = "Timeout in minutes for each VM start")
   protected long timeout = 5L;

   @Option(names = { "-includes",
         "--includes" }, split = ";", description = "Testcases for inclusion (default: empty, includes all tests). Example: \"my.package.Clazz#myMethod;my.otherpackage.ClazzB#*\"")
   protected String[] includes;

   @Option(names = { "-excludes",
         "--excludes" }, split = ";", description = "Testcases for exclusion (default: empty, excludes no test). Example: \"my.package.Clazz#myMethod;my.otherpackage.ClazzB#*\"")
   protected String[] excludes;

   @Option(names = { "-includeByRule",
         "--includeByRule" }, split = ";", description = "Rules that should be included (if defined, only test classes having exactly this rule(s) will be used)")
   protected String[] includeByRule;

   @Option(names = { "-excludeByRule",
         "--excludeByRule" }, split = ";", description = "Rules that should be included (if defined, test classes will be excluded that use this rule, even if included by includeByRule or includes)")
   protected String[] excludeByRule;

   @Option(names = { "-forbiddenMethods", "--forbiddenMethods" }, description = "Testcases that call one of these methods are excluded")
   protected String[] forbiddenMethods;

   @Option(names = { "-startcommit", "--startcommit" }, description = "First commit that should be analysed - do not use together with commit and commitOld!")
   protected String startcommit;

   @Option(names = { "-endcommit", "--endcommit" }, description = "Last commit that should be analysed - do not use together with commit and commitOld! ")
   protected String endcommit;

   @Option(names = { "-testGoal", "--testGoal" }, description = "Test goal that should be used; default testRelease for Android projects and test for all others. "
         + "If you want to use test<VariantName> for Android, please specify a goal (i.e. task name) here."
         + "If you want to run integration tests in maven e.g. by calling failsafe, also specify it here. ")
   protected String testGoal;

   @Option(names = { "-cleanGoal", "--cleanGoal" }, description = "Clean goal that is called before the test execution *in Gradle*; defaults to cleanTest.")
   protected String cleanGoal;

   @Option(names = { "-pl", "--pl" }, description = "Projectlist (-pl) argument for maven (e.g. :submodule) - only the submodule and its dependencies are analyzed (using -am)")
   protected String pl;

   @Option(names = { "-workloadType", "--workloadType" }, description = "Which workload should be executed - by default JUNIT, can be changed to JMH")
   public WorkloadType workloadType = WorkloadType.JUNIT;

   @Option(names = { "-testExecutor", "--testExecutor" }, description = "Set test executor (should be specified by plugin; not usable with pure Peass)")
   public String testExecutor;

   @Option(names = { "-testTransformer", "--testTransformer" }, description = "Set test transformer (should be specified by plugin; not usable with pure Peass)")
   public String testTransformer;

   @Option(names = { "-gitCryptKey", "--gitCryptKey" }, description = "If repository uses git-crypt, you should provide location of git-crypt-keyfile)")
   protected String gitCryptKey = System.getenv("GIT_CRYPT_KEY");

   @Option(names = { "-useTieredCompilation", "--useTieredCompilation" }, description = "Activate -XX:-TieredCompilation for all measured processes")
   protected boolean useTieredCompilation = false;

   @Option(names = { "-executeBeforeClassInMeasurement", "--executeBeforeClassInMeasurement" }, description = "Execute @BeforeClass / @BeforeAll in measurement loop")
   protected boolean executeBeforeClassInMeasurement = false;

   @Option(names = { "-clearMockitoCaches",
         "--clearMockitoCaches" }, description = "Clear Mockito cache by adding a method that calls Mockito.clearAllCaches() in every repetition")
   protected boolean clearMockitoCaches = false;

   @Option(names = { "-removeSnapshots",
         "--removeSnapshots" }, description = "Activates removing SNAPSHOTS (if older versions should be analysed, this should be activated; for performance measurement in CI, this should not be activated)")
   protected boolean removeSnapshots = false;

   @Option(names = { "-useAlternativeBuildfile",
         "--useAlternativeBuildfile" }, description = "Use alternative buildfile when existing (searches for alternative_build.gradle and replaces build.gradle with the file; required e.g. if the default build process contains certification)")
   protected boolean useAlternativeBuildfile = false;

   @Option(names = { "-classFolder", "--classFolder" }, description = "Folder that contains java classes")
   protected String clazzFolder = CLAZZ_FOLDERS_DEFAULT;

   @Option(names = { "-testClassFolder", "--testClassFolder" }, description = "Folder that contains test classes")
   protected String testClazzFolder = TEST_FOLDERS_DEFAULT;

   @Option(names = { "-excludeLog4jToSlf4j", "--excludeLog4jToSlf4j" }, description = "Exclude log4j-to-slf4j (required, if other logging implementation should be used)")
   protected boolean excludeLog4jToSlf4j = false;

   @Option(names = { "-excludeLog4jSlf4jImpl", "--excludeLog4jSlf4jImpl" }, description = "Exclude log4j-slf4j-impl (required, if other logging implementation should be used)")
   protected boolean excludeLog4jSlf4jImpl = false;

   @Option(names = { "-dontRedirectToNull",
         "--dontRedirectToNull" }, description = "Activates showing the standard output of the testcase (by default, it is redirected to null)")
   protected boolean dontRedirectToNull = false;

   @Option(names = { "-showStart",
         "--showStart" }, description = "Activates showing and end of each KoPeMe iteration (default false, and always activated for regression test selection)")
   protected boolean showStart = false;

   @Option(names = { "-onlyMeasureWorkload", "--onlyMeasureWorkload" }, description = "Only measure workload (no @Before/@After)")
   protected boolean onlyMeasureWorkload = false;

   @Option(names = { "-properties", "--properties" }, description = "Sets the properties that should be passed to the test (e.g. \"-Dmy.var=5\")")
   public String properties;

   @Option(names = { "-heapSize",
         "--heapSize" }, description = "Sets the heap size of the child VMs by setting something like -Xmx5g in the buildfile; only pass the size (e.g. 5g or 2048m, not -Xmx5g nor -Xmx2024m)")
   public String heapSize;

   @Option(names = { "-useAnbox",
         "--useAnbox" }, description = "Activates usage of Anbox measurement features (currently experimental)")
   protected boolean useAnbox = false;

   @Option(names = { "-androidManifest", "--androidManifest" }, description = "Sets the relative path to the main Android manifest file (e.g. app/src/main/AndroidManifest.xml)")
   protected String androidManifest;

   @Option(names = { "-increaseVariableValues",
         "--increaseVariableValues" }, split = ";", description = "List of variables and values to be modified (default: empty). Example: \"package.Clazz.variable:value;otherPackage.otherClazz.otherVariable:otherValue\"")
   protected String[] increaseVariableValues;

   public long getTimeout() {
      return timeout;
   }

   public void setTimeout(final long timeout) {
      this.timeout = timeout;
   }

   public void setIncludes(final String[] includes) {
      this.includes = includes;
   }

   public String[] getIncludes() {
      return includes;
   }

   public void setIncludeByRule(String[] includeByRule) {
      this.includeByRule = includeByRule;
   }

   public String[] getIncludeByRule() {
      return includeByRule;
   }

   public void setExcludeByRule(String[] excludeByRule) {
      this.excludeByRule = excludeByRule;
   }

   public String[] getExcludeByRule() {
      return excludeByRule;
   }

   public void setTestGoal(final String testGoal) {
      this.testGoal = testGoal;
   }

   public String getTestGoal() {
      return testGoal;
   }

   public void setCleanGoal(String cleanGoal) {
      this.cleanGoal = cleanGoal;
   }

   public String getCleanGoal() {
      return cleanGoal;
   }

   public String getStartcommit() {
      return startcommit;
   }

   public void setStartcommit(String startcommit) {
      this.startcommit = startcommit;
   }

   public String getEndcommit() {
      return endcommit;
   }

   public void setEndcommit(String endcommit) {
      this.endcommit = endcommit;
   }

   public void setPl(final String pl) {
      this.pl = pl;
   }

   public String getPl() {
      return pl;
   }

   public boolean isRemoveSnapshots() {
      return removeSnapshots;
   }

   public void setRemoveSnapshots(final boolean removeSnapshots) {
      this.removeSnapshots = removeSnapshots;
   }

   public boolean isUseAlternativeBuildfile() {
      return useAlternativeBuildfile;
   }

   public void setUseAlternativeBuildfile(final boolean useAlternativeBuildfile) {
      this.useAlternativeBuildfile = useAlternativeBuildfile;
   }

   public String[] getExcludes() {
      return excludes;
   }

   public void setExcludes(final String[] excludes) {
      this.excludes = excludes;
   }

   public String[] getForbiddenMethods() {
      return forbiddenMethods;
   }

   public void setForbiddenMethods(String[] forbiddenMethods) {
      this.forbiddenMethods = forbiddenMethods;
   }

   public WorkloadType getWorkloadType() {
      return workloadType;
   }

   public void setWorkloadType(final WorkloadType workloadType) {
      this.workloadType = workloadType;
   }

   public String getTestExecutor() {
      return testExecutor;
   }

   public void setTestExecutor(final String testExecutor) {
      this.testExecutor = testExecutor;
   }

   public String getTestTransformer() {
      return testTransformer;
   }

   public void setTestTransformer(final String testTransformer) {
      this.testTransformer = testTransformer;
   }

   public String getGitCryptKey() {
      return gitCryptKey;
   }

   public void setGitCryptKey(final String gitCryptKey) {
      this.gitCryptKey = gitCryptKey;
   }

   public boolean isUseTieredCompilation() {
      return useTieredCompilation;
   }

   public void setUseTieredCompilation(final boolean useTieredCompilation) {
      this.useTieredCompilation = useTieredCompilation;
   }

   public boolean isExecuteBeforeClassInMeasurement() {
      return executeBeforeClassInMeasurement;
   }

   public void setExecuteBeforeClassInMeasurement(final boolean executeBeforeClassInMeasurement) {
      this.executeBeforeClassInMeasurement = executeBeforeClassInMeasurement;
   }

   public boolean isClearMockitoCaches() {
      return clearMockitoCaches;
   }

   public void setClearMockitoCaches(boolean clearMockitoCaches) {
      this.clearMockitoCaches = clearMockitoCaches;
   }

   public String getClazzFolder() {
      return clazzFolder;
   }

   public void setClazzFolder(final String clazzFolder) {
      this.clazzFolder = clazzFolder;
   }

   public String getTestClazzFolder() {
      return testClazzFolder;
   }

   public void setTestClazzFolder(final String testClazzFolder) {
      this.testClazzFolder = testClazzFolder;
   }

   public boolean isExcludeLog4jSlf4jImpl() {
      return excludeLog4jSlf4jImpl;
   }

   public void setExcludeLog4jSlf4jImpl(boolean excludeLog4jSlf4jImpl) {
      this.excludeLog4jSlf4jImpl = excludeLog4jSlf4jImpl;
   }

   public boolean isExcludeLog4jToSlf4j() {
      return excludeLog4jToSlf4j;
   }

   public void setExcludeLog4jToSlf4j(boolean excludeLog4jToSlf4j) {
      this.excludeLog4jToSlf4j = excludeLog4jToSlf4j;
   }

   public boolean isDontRedirectToNull() {
      return dontRedirectToNull;
   }

   public void setDontRedirectToNull(final boolean dontRedirectToNull) {
      this.dontRedirectToNull = dontRedirectToNull;
   }

   public boolean isOnlyMeasureWorkload() {
      return onlyMeasureWorkload;
   }

   public void setOnlyMeasureWorkload(boolean onlyMeasureWorkload) {
      this.onlyMeasureWorkload = onlyMeasureWorkload;
   }

   public String getHeapSize() {
      return heapSize;
   }

   public void setHeapSize(String heapSize) {
      this.heapSize = heapSize;
   }

   public String getProperties() {
      return properties;
   }

   public void setProperties(final String properties) {
      this.properties = properties;
   }

   public boolean isUseAnbox() {
      return useAnbox;
   }

   public void setUseAnbox(boolean useAnbox) {
      this.useAnbox = useAnbox;
   }

   public String getAndroidManifest() {
      return androidManifest;
   }

   public void setAndroidManifest(String androidManifest) {
      this.androidManifest = androidManifest;
   }

   public String[] getIncreaseVariableValues() {
      return increaseVariableValues;
   }

   public void setIncreaseVariableValues(final String[] increaseVariableValues) {
      this.increaseVariableValues = increaseVariableValues;
   }

   public ExecutionConfig getExecutionConfig() {
      ExecutionConfig config = new ExecutionConfig(timeout);

      config.setStartcommit(getStartcommit());
      config.setEndcommit(getEndcommit());
      config.setTestGoal(getTestGoal());
      config.setCleanGoal(getCleanGoal());

      if (getIncludes() != null) {
         for (String include : getIncludes()) {
            config.getIncludes().add(include);
         }
      }
      if (getExcludes() != null) {
         for (String exclude : getExcludes()) {
            config.getExcludes().add(exclude);
         }
      }
      if (getIncludeByRule() != null) {
         for (String includeByRule : getIncludeByRule()) {
            config.getIncludeByRule().add(includeByRule);
         }
      }
      if (getExcludeByRule() != null) {
         for (String excludeByRule : getExcludeByRule()) {
            config.getExcludeByRule().add(excludeByRule);
         }
      }

      if (getForbiddenMethods() != null) {
         for (String forbiddenMethod : getForbiddenMethods()) {
            config.getForbiddenMethods().add(forbiddenMethod);
         }
      }

      if (getPl() != null) {
         config.setPl(pl);
      }
      boolean transformerSet = getTestTransformer() != null;
      boolean executorSet = getTestExecutor() != null;
      if (transformerSet && executorSet) {
         config.setTestTransformer(getTestTransformer());
         config.setTestExecutor(getTestExecutor());
      } else if (transformerSet && !executorSet) {
         throw new RuntimeException("If --testTransformer is set by CLI parameters, --testExecutor needs also be set!");
      } else if (!transformerSet && executorSet) {
         throw new RuntimeException("If --testExecutor is set by CLI parameters, --testTransformer needs also be set!");
      } else {
         config.setTestTransformer(getWorkloadType().getTestTransformer());
         config.setTestExecutor(getWorkloadType().getTestExecutor());
      }
      config.setGitCryptKey(getGitCryptKey());
      config.setUseTieredCompilation(useTieredCompilation);
      config.setRemoveSnapshots(removeSnapshots);
      config.setUseAlternativeBuildfile(useAlternativeBuildfile);
      config.setRemoveSnapshots(removeSnapshots);
      config.setExecuteBeforeClassInMeasurement(executeBeforeClassInMeasurement);
      config.setClearMockitoCaches(clearMockitoCaches);
      config.setProperties(properties);
      config.setXmx(heapSize);

      if (getClazzFolder() != null) {
         List<String> clazzFolders = ExecutionConfig.buildFolderList(getClazzFolder());
         config.setClazzFolders(clazzFolders);
      }
      if (getTestClazzFolder() != null) {
         List<String> testClazzFolders = ExecutionConfig.buildFolderList(getTestClazzFolder());
         config.setTestClazzFolders(testClazzFolders);
      }

      config.setExcludeLog4jSlf4jImpl(excludeLog4jSlf4jImpl);
      config.setExcludeLog4jToSlf4j(excludeLog4jToSlf4j);
      config.setRedirectToNull(!dontRedirectToNull);
      config.setShowStart(showStart);
      config.setOnlyMeasureWorkload(onlyMeasureWorkload);

      if (config.isExecuteBeforeClassInMeasurement() && config.isOnlyMeasureWorkload()) {
         throw new RuntimeException("executeBeforeClassInMeasurement may only be activated if onlyMeasureWorkload is deactivated!");
      }

      if (config.isClearMockitoCaches() && !config.isExecuteBeforeClassInMeasurement()) {
         throw new RuntimeException("Currently, clearMockitoCaches is only allowed if executeBeforeClassInMeasurement is also activated!");
      }

      config.setUseAnbox(useAnbox);
      config.setAndroidManifest(androidManifest);

      if (getIncreaseVariableValues() != null) {
         for (String variable : getIncreaseVariableValues()) {
            config.getIncreaseVariableValues().add(variable);
         }
      }

      return config;
   }
}
