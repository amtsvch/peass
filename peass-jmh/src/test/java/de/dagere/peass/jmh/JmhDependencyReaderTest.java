package de.dagere.peass.jmh;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.javaparser.ParseException;

import de.dagere.peass.TestConstants;
import de.dagere.peass.TestUtil;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.config.KiekerConfig;
import de.dagere.peass.config.TestSelectionConfig;
import de.dagere.peass.dependency.analysis.data.CommitDiff;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.persistence.InitialCallList;
import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependency.reader.CommitKeeper;
import de.dagere.peass.dependencytests.FakeGitUtil;
import de.dagere.peass.dependencytests.TraceGettingIT;
import de.dagere.peass.dependencytests.helper.FakeFileIterator;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;

public class JmhDependencyReaderTest {

   @BeforeEach
   public void clearCurrent() throws IOException {
      TestUtil.deleteContents(TestConstants.CURRENT_FOLDER);
   }

   static class KiekerConfigurationProvider implements ArgumentsProvider {
      @Override
      public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
         KiekerConfig regularConfiguration = new KiekerConfig(true);
         KiekerConfig aspectJConfiguration = new KiekerConfig(true);
         aspectJConfiguration.setUseSourceInstrumentation(false);
         return Stream.of(
               Arguments.of(regularConfiguration),
               Arguments.of(aspectJConfiguration));
      }
   }

   @ParameterizedTest
   @ArgumentsSource(KiekerConfigurationProvider.class)
   public void testVersionReading(final KiekerConfig kiekerConfig)
         throws IOException, InterruptedException, XmlPullParserException, ParseException, ClassNotFoundException,
         InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
      try (MockedStatic<GitUtils> gitUtilsMock = Mockito.mockStatic(GitUtils.class)) {
         FakeGitUtil.prepareGitUtils(gitUtilsMock);
         FakeFileIterator iterator = mockIterator();

         ResultsFolders resultsFolders = new ResultsFolders(TraceGettingIT.VIEW_IT_PROJECTFOLDER, "test");

         TestSelectionConfig dependencyConfig = new TestSelectionConfig(1, false, true, false, false, true);

         ExecutionConfig jmhConfig = new ExecutionConfig();
         jmhConfig.setTestTransformer("de.dagere.peass.dependency.jmh.JmhTestTransformer");
         jmhConfig.setTestExecutor("de.dagere.peass.dependency.jmh.JmhTestExecutor");

         DependencyReader reader = new DependencyReader(dependencyConfig, new PeassFolders(TestConstants.CURRENT_FOLDER), resultsFolders,
               "", iterator, new CommitKeeper(new File("/dev/null")), jmhConfig, kiekerConfig, new EnvironmentVariables());
         reader.readInitialCommit();

         checkInitialVersion(resultsFolders);

         reader.readDependencies();

         checkChangedVersion(resultsFolders);
      }
   }

   private void checkChangedVersion(final ResultsFolders resultsFolders) throws IOException, JsonParseException, JsonMappingException {
      ExecutionData data = Constants.OBJECTMAPPER.readValue(resultsFolders.getTraceTestSelectionFile(), ExecutionData.class);
      TestMethodCall changedBenchmark = new TestMethodCall("de.dagere.peass.ExampleBenchmark", "testMethod");
      MatcherAssert.assertThat(data.getCommits().get("000002").getTestMethods(), Matchers.contains(changedBenchmark));
   }

   private void checkInitialVersion(final ResultsFolders resultsFolders) throws IOException, JsonParseException, JsonMappingException {
      StaticTestSelection dependencies = Constants.OBJECTMAPPER.readValue(resultsFolders.getStaticTestSelectionFile(), StaticTestSelection.class);
      Map<TestMethodCall, InitialCallList> initialDependencies = dependencies.getInitialcommit().getInitialDependencies();
      MatcherAssert.assertThat(initialDependencies.keySet(), Matchers.hasSize(1));
      InitialCallList initial = initialDependencies.get(new TestMethodCall("de.dagere.peass.ExampleBenchmark", "testMethod", ""));
      MatcherAssert.assertThat(initial.getEntities(), Matchers.hasSize(2));
   }

   private FakeFileIterator mockIterator() {
      List<File> versionList = Arrays.asList(JmhTestConstants.BASIC_VERSION, JmhTestConstants.SLOWER_VERSION);

      FakeFileIterator fakeIterator = new FakeFileIterator(TestConstants.CURRENT_FOLDER, versionList);
      fakeIterator.goToFirstCommit();
      FakeFileIterator iteratorspied = Mockito.spy(fakeIterator);
      CommitDiff fakedDiff = new CommitDiff(Arrays.asList(TestConstants.CURRENT_FOLDER), TestConstants.CURRENT_FOLDER);
      ExecutionConfig defaultConfig = new ExecutionConfig();
      fakedDiff.addChange("src/test/java/de/dagere/peass/ExampleBenchmark.java", defaultConfig);

      Mockito.doReturn(fakedDiff)
            .when(iteratorspied)
            .getChangedClasses(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
      return iteratorspied;
   }
}
