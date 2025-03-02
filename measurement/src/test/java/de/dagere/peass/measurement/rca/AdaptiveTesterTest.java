package de.dagere.peass.measurement.rca;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.dagere.kopeme.kopemedata.VMResult;
import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.dependency.ExecutorCreator;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependencyprocessors.CommitByNameComparator;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.measurement.dataloading.ResultLoader;
import de.dagere.peass.measurement.dependencyprocessors.AdaptiveTester;
import de.dagere.peass.measurement.dependencyprocessors.DependencyTester;
import de.dagere.peass.measurement.dependencyprocessors.reductioninfos.ReductionManager;
import de.dagere.peass.measurement.organize.ResultOrganizer;
import de.dagere.peass.measurement.rca.helper.VCSTestUtils;
import de.dagere.peass.testtransformation.JUnitTestTransformer;
import de.dagere.peass.vcs.VersionControlSystem;

public class AdaptiveTesterTest {

   private final TestMethodCall testcase = new TestMethodCall("Dummy", "dummyTest");
   private JUnitTestTransformer testGenerator = Mockito.mock(JUnitTestTransformer.class);

   private ReductionManager reductionManagerSpy;

   @Rule
   public TemporaryFolder folder = new TemporaryFolder();

   @Test
   public void testIterationUpdate() throws IOException {
      try (MockedStatic<VersionControlSystem> mockedVCS = Mockito.mockStatic(VersionControlSystem.class);
            MockedStatic<ExecutorCreator> mockedExecutor = Mockito.mockStatic(ExecutorCreator.class)) {
         VCSTestUtils.mockGetVCS(mockedVCS);
         VCSTestUtils.mockExecutor(mockedExecutor);

         final int vms = 10;
         final MeasurementConfig config = new MeasurementConfig(vms, "A", "B");
         config.setIterations(1000);
         config.setWaitTimeBetweenVMs(0);

         MeasurementConfig config2 = Mockito.spy(config);
         Mockito.when(testGenerator.getConfig()).thenReturn(config2);

         AdaptiveTester tester2 = prepareTester();
         mockResultOrganizer(tester2);
         Mockito.doReturn(false).when(tester2).checkIsDecidable(Mockito.eq(testcase), Mockito.anyInt());

         for (int i = 0; i < vms; i++) {
            final VMResult result1 = new VMResult();
            result1.setValue(15);
            result1.setIterations(40);
            Mockito.doReturn(result1).when(reductionManagerSpy).getLastResult(Mockito.eq("A"), Mockito.eq(testcase), Mockito.eq(i), Mockito.any());

            final VMResult result2 = new VMResult();
            result2.setValue(17);
            result2.setIterations(40);
            Mockito.doReturn(result2).when(reductionManagerSpy).getLastResult(Mockito.eq("B"), Mockito.eq(testcase), Mockito.eq(i), Mockito.any());
         }

         tester2.evaluate(testcase);

         Assert.assertEquals(vms, tester2.getFinishedVMs());
         Mockito.verify(config2).setIterations(38);
      }

   }

   private void mockResultOrganizer(AdaptiveTester tester2) {
      ResultOrganizer resultOrganizerMock = Mockito.mock(ResultOrganizer.class);
      Mockito.doReturn(new PeassFolders(new File("target"))).when(resultOrganizerMock).getFolders();
      Mockito.doReturn(resultOrganizerMock).when(tester2).getCurrentOrganizer();
   }

   @Ignore
   @Test
   public void testEarlyDecision() throws Exception {
      ResultLoader loader = Mockito.mock(ResultLoader.class);
      Mockito.when(loader.getStatisticsAfter()).thenReturn(new DescriptiveStatistics(new double[] { 15, 15, 15, 15, 15 }));
      Mockito.when(loader.getStatisticsBefore()).thenReturn(new DescriptiveStatistics(new double[] { 15, 15, 15, 15, 15 }));

      final MeasurementConfig config = new MeasurementConfig(100, "A", "B");
      config.setIterations(1000);

      MeasurementConfig config2 = Mockito.spy(config);
      Mockito.when(testGenerator.getConfig()).thenReturn(config2);

      AdaptiveTester tester2 = prepareTester();
      
      createEarlyBreakData(tester2);

      tester2.evaluate(testcase);

      Assert.assertEquals(31, tester2.getFinishedVMs());
   }

   @Test
   public void testSkipEarlyDecision() throws IOException {
      final MeasurementConfig config = new MeasurementConfig(100, "A", "B");
      config.setIterations(1000);
      config.setEarlyStop(false);
      config.setWaitTimeBetweenVMs(0);

      MeasurementConfig config2 = Mockito.spy(config);
      Mockito.when(testGenerator.getConfig()).thenReturn(config2);

      AdaptiveTester tester2 = prepareTester();
      mockResultOrganizer(tester2);
      
      createEarlyBreakData(tester2);

      tester2.evaluate(testcase);

      Assert.assertEquals(100, tester2.getFinishedVMs());
   }

   private void createEarlyBreakData(final AdaptiveTester tester2) {
      for (int i = 0; i < 100; i++) {
         final VMResult result1 = new VMResult();
         result1.setValue(15);
         result1.setIterations(40);
         Mockito.doReturn(result1).when(reductionManagerSpy).getLastResult(Mockito.eq("A"), Mockito.eq(testcase), Mockito.eq(i), Mockito.any());

         final VMResult result2 = new VMResult();
         result2.setValue(15);
         result2.setIterations(40);
         Mockito.doReturn(result2).when(reductionManagerSpy).getLastResult(Mockito.eq("B"), Mockito.eq(testcase), Mockito.eq(i), Mockito.any());
      }
   }

   private AdaptiveTester prepareTester() throws IOException {
      final PeassFolders folders = Mockito.mock(PeassFolders.class);
      Mockito.when(folders.getProjectFolder()).thenReturn(folder.newFolder("test"));
      Mockito.when(folders.getProgressFile()).thenReturn(folder.newFile("progress"));
      Mockito.when(folders.getResultFile(Mockito.any(TestMethodCall.class), Mockito.anyInt(), Mockito.anyString(), Mockito.anyString()))
            .thenAnswer((index) -> {
               return new File(folder.getRoot(), "log" + index);
            });
      Mockito.when(folders.getMeasureLogFolder(Mockito.anyString(), Mockito.any(TestMethodCall.class))).thenReturn(folder.newFile("log"));

      AdaptiveTester tester = new AdaptiveTester(folders, testGenerator.getConfig(), new EnvironmentVariables(), CommitByNameComparator.INSTANCE);
      AdaptiveTester tester2 = Mockito.spy(tester);

      ReductionManager reductionManager = new ReductionManager(testGenerator.getConfig());
      reductionManagerSpy = Mockito.spy(reductionManager);

      try {
         Field privateField = DependencyTester.class.getDeclaredField("reductionManager");
         privateField.setAccessible(true);

         privateField.set(tester2, reductionManagerSpy);
      } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
         e.printStackTrace();
      }

      Mockito.doNothing().when(tester2).runOneComparison(Mockito.any(File.class), Mockito.any(TestMethodCall.class), Mockito.anyInt());
      return tester2;
   }

}
