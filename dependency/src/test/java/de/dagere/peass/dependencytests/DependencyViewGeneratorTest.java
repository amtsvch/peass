package de.dagere.peass.dependencytests;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import de.dagere.peass.TestConstants;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.KiekerConfig;
import de.dagere.peass.config.TestSelectionConfig;
import de.dagere.peass.dependency.analysis.data.CommitDiff;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependency.reader.CommitKeeper;
import de.dagere.peass.dependencytests.helper.FakeFileIterator;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;

public class DependencyViewGeneratorTest {

   private static final Logger LOG = LogManager.getLogger(TraceGettingIT.class);

   @Test
   public void testTwoVersions() throws Exception {
      try (MockedStatic<GitUtils> gitUtilsMock = Mockito.mockStatic(GitUtils.class)) {
         FakeGitUtil.prepareGitUtils(gitUtilsMock);

         DependencyDetectorTestUtil.init(TraceGettingIT.BASIC);

         ResultsFolders resultsFolders = new ResultsFolders(TraceGettingIT.VIEW_IT_PROJECTFOLDER, "test");

         TestSelectionConfig dependencyConfig = new TestSelectionConfig(1, false, true, false, false, true);

         FakeFileIterator iteratorspied = mockIterator();

         DependencyReader reader = new DependencyReader(dependencyConfig, new PeassFolders(TestConstants.CURRENT_FOLDER), resultsFolders,
               "", iteratorspied, new CommitKeeper(new File("/dev/null")), new ExecutionConfig(), new KiekerConfig(true), new EnvironmentVariables());
         reader.readInitialCommit();
         try {
            reader.readDependencies();
         } catch (Throwable t) {
            System.out.println();
            for (StackTraceElement te : t.getStackTrace()) {
               System.out.println(te);
            }
            System.out.println();
            throw t;
         }
         

         File expectedDiff = new File(resultsFolders.getVersionDiffFolder("000002"), "TestMe#test.zip");
         System.out.println(expectedDiff.getAbsolutePath());
         Assert.assertTrue(expectedDiff.exists());

         // TODO Test, that instrumentation sources are not added to the view

         final ExecutionData tests = Constants.OBJECTMAPPER.readValue(resultsFolders.getTraceTestSelectionFile(), ExecutionData.class);
         //
         Assert.assertEquals(2, tests.getCommits().size());
         Assert.assertEquals(1, tests.getCommits().get("000002").getTestMethods().size());
      }

   }

   private FakeFileIterator mockIterator() {
      List<File> versionList = Arrays.asList(TraceGettingIT.BASIC, TraceGettingIT.REPETITION);

      FakeFileIterator fakeIterator = new FakeFileIterator(TestConstants.CURRENT_FOLDER, versionList);
      fakeIterator.goToFirstCommit();
      FakeFileIterator iteratorspied = Mockito.spy(fakeIterator);
      CommitDiff fakedDiff = new CommitDiff(Arrays.asList(TestConstants.CURRENT_FOLDER), TestConstants.CURRENT_FOLDER);
      ExecutionConfig defaultConfig = new ExecutionConfig();
      fakedDiff.addChange("src/test/java/viewtest/TestMe.java", defaultConfig);

      Mockito.doReturn(fakedDiff)
            .when(iteratorspied)
            .getChangedClasses(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
      return iteratorspied;
   }
}
