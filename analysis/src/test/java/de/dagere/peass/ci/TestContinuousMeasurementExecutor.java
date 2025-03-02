package de.dagere.peass.ci;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import de.dagere.kopeme.datastorage.JSONDataStorer;
import de.dagere.kopeme.kopemedata.DatacollectorResult;
import de.dagere.kopeme.kopemedata.Kopemedata;
import de.dagere.kopeme.kopemedata.VMResult;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.config.MeasurementStrategy;
import de.dagere.peass.dependency.ExecutorCreator;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependencyprocessors.CommitByNameComparator;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.execution.utils.TestExecutor;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.measurement.dataloading.MeasurementFileFinder;
import de.dagere.peass.measurement.dataloading.ResultLoader;
import de.dagere.peass.measurement.dependencyprocessors.OnceRunner;

public class TestContinuousMeasurementExecutor {

   private static final int ITERATIONS = 50;

   private static final TestMethodCall TEST1 = new TestMethodCall("de.dagere.peass.TestClazzA", "test1");
   private static final TestMethodCall TEST2 = new TestMethodCall("de.dagere.peass.TestClazzB", "test2");
   private static final TestMethodCall TEST3 = new TestMethodCall("de.dagere.peass.TestClazzB", "test4");
   private static final TestMethodCall TEST4 = new TestMethodCall("de.dagere.peass.TestClazzC", "test3");

   private final File parentFile = new File("target/continuousMeasurementExecutor/");

   @BeforeEach
   public void initFolder() throws IOException {
      if (parentFile.exists()) {
         FileUtils.cleanDirectory(parentFile);
      }
      parentFile.mkdirs();
   }

   @Test
   public void testConfigurationChange() throws IOException, InterruptedException, XmlPullParserException {
      try (MockedStatic<ExecutorCreator> executorCreatorMock = Mockito.mockStatic(ExecutorCreator.class)) {

         mockExecutorCreation();

         File projectFolder = new File(parentFile, "project");
         projectFolder.mkdirs();
         final PeassFolders folders = new PeassFolders(projectFolder);

         MeasurementConfig measurementConfig = createMeasurementConfig();
         ContinuousMeasurementExecutor cme = new ContinuousMeasurementExecutor(folders, measurementConfig, new EnvironmentVariables(), CommitByNameComparator.INSTANCE);

         Set<TestMethodCall> tests = buildTestSet();

         File logFile = new File(parentFile, "log.txt");
         File fullResultsVersion = new File(parentFile, "fullResultsVersion");

         Answer<Void> answerOnceRunner = mockOnceRunner(folders);

         Answer<Object> answerResultLoader = mockResultLoader();
         try (MockedConstruction<OnceRunner> onceRunncerConstruction = Mockito.mockConstructionWithAnswer(OnceRunner.class, answerOnceRunner);
               MockedConstruction<ResultLoader> resultLoaderConstruction = Mockito.mockConstructionWithAnswer(ResultLoader.class, answerResultLoader)) {
            File measurementResultFolder = cme.executeMeasurements(tests, fullResultsVersion, logFile);

            checkResults(executorCreatorMock, measurementConfig, measurementResultFolder);
         }
      }
   }

   private void checkResults(MockedStatic<ExecutorCreator> executorCreatorMock, MeasurementConfig measurementConfig, File measurementResultFolder) {
      Assert.assertEquals(measurementConfig.getIterations(), ITERATIONS);

      final ArgumentCaptor<MeasurementConfig> capturedConfig = ArgumentCaptor.forClass(MeasurementConfig.class);
      executorCreatorMock.verify(() -> {
         ExecutorCreator.createTestTransformer(Mockito.any(), Mockito.any(), capturedConfig.capture());
      }, Mockito.times(36));
      MeasurementConfig finalConfig = capturedConfig.getAllValues().get(0);

      // This only checks that the iterations are correctly after the end; in theory, we could also check whether the reduction for TEST2 and TEST4 work correctly
      Assert.assertEquals(finalConfig.getIterations(), ITERATIONS);

      File resultFileTest1 = new File(measurementResultFolder, "de.dagere.peass.TestClazzA/000001/000001/test1_4_000001.json");
      Assert.assertTrue(resultFileTest1.exists());

      File resultFileTest3 = new File(measurementResultFolder, "de.dagere.peass.TestClazzB/000001/000001/test4_4_000001.json");
      Assert.assertTrue(resultFileTest3.exists());
   }

   private MeasurementConfig createMeasurementConfig() {
      MeasurementConfig measurementConfig = new MeasurementConfig(5);
      measurementConfig.getFixedCommitConfig().setCommit("000001");
      measurementConfig.getFixedCommitConfig().setCommitOld("000000");
      measurementConfig.setIterations(ITERATIONS);
      measurementConfig.setCallSyncBetweenVMs(false);
      measurementConfig.setWaitTimeBetweenVMs(0);
      measurementConfig.getExecutionConfig().setRedirectSubprocessOutputToFile(false);
      
      // Measurement needs to be done sequential, since static mocking only works in the same Thread
      measurementConfig.setMeasurementStrategy(MeasurementStrategy.SEQUENTIAL);
      return measurementConfig;
   }

   private Answer<Object> mockResultLoader() {
      Answer<Object> answerResultLoader = new Answer<Object>() {

         private TestCase lastTest;

         @Override
         public Object answer(InvocationOnMock invocation) throws Throwable {
            System.out.println("Mocking " + invocation.getMethod().getName() + " for " + lastTest);
            if (invocation.getMethod().getName().equals("getStatisticsAfter")) {
               if (lastTest.equals(TEST1) || lastTest.equals(TEST3)) {
                  return new DescriptiveStatistics(new double[] { 1, 2, 3 });
               } else {
                  return null;
               }
            }
            if (invocation.getMethod().getName().equals("getStatisticsBefore")) {
               if (lastTest.equals(TEST1) || lastTest.equals(TEST3)) {
                  return new DescriptiveStatistics(new double[] { 1, 2, 3 });
               } else {
                  return null;
               }
            }
            lastTest = invocation.getArgument(1);

            return null;
         }
      };
      return answerResultLoader;
   }

   private Answer<Void> mockOnceRunner(final PeassFolders folders) {
      Answer<Void> answerOnceRunner = new Answer<Void>() {

         @Override
         public Void answer(InvocationOnMock invocation) throws Throwable {
            TestMethodCall testcase = invocation.getArgument(0);
            String version = invocation.getArgument(1);
            int vmId = invocation.getArgument(2);
            System.out.println("Running test " + testcase + " for version " + version + " and vmId " + vmId);

            if (testcase.equals(TEST1) || testcase.equals(TEST3)) {
               Kopemedata data = new Kopemedata("");
               DatacollectorResult dataCollector = MeasurementFileFinder.getDataCollector(testcase.getMethod(), data.getMethods());
               VMResult result = new VMResult();
               result.setValue(50);
               result.setIterations(ITERATIONS);
               dataCollector.getResults().add(result);

               File resultFile = folders.getResultFile(testcase, vmId, version, "000001");
               resultFile.getParentFile().mkdirs();
               JSONDataStorer.storeData(resultFile, data);
            }
            return null;
         }
      };
      return answerOnceRunner;
   }

   private Set<TestMethodCall> buildTestSet() {
      Set<TestMethodCall> tests = new HashSet<>();
      tests.add(TEST1);
      tests.add(TEST2);
      tests.add(TEST3);
      tests.add(TEST4);
      return tests;
   }

   private void mockExecutorCreation() {
      TestExecutor mockedExecutor = Mockito.mock(TestExecutor.class);

      Mockito.doAnswer(new Answer<Void>() {

         @Override
         public Void answer(InvocationOnMock invocation) throws Throwable {
            System.out.println("Running " + invocation.getArgument(0));
            return null;
         }
      }).when(mockedExecutor).executeTest(Mockito.any(), Mockito.any(), Mockito.anyLong());
      Mockito.when(ExecutorCreator.createExecutor(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(mockedExecutor);
   }
}
