package de.dagere.peass.dependencytests;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import com.github.javaparser.ParseException;

import de.dagere.peass.dependency.ChangeManager;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.analysis.testData.TestMethodCall;
import de.dagere.peass.dependency.reader.DependencyReader;
import de.dagere.peass.dependencytests.helper.FakeFileIterator;
import de.dagere.peass.vcs.CommitIterator;

public class DependencyDetectorJava11IT {

   @BeforeEach
   public void initialize() throws IOException, InterruptedException {
      Assert.assertTrue(DependencyTestConstants.VERSIONS_FOLDER.exists());

      FileUtils.deleteDirectory(DependencyTestConstants.CURRENT);
      FileUtils.copyDirectory(DependencyTestConstants.BASIC_STATE, DependencyTestConstants.CURRENT);
      overwritePom();
   }

   private void overwritePom() throws IOException {
      FileUtils.copyFile(new File(DependencyTestConstants.VERSIONS_FOLDER, "pom-11.xml"), new File(DependencyTestConstants.CURRENT, "pom.xml"));
   }

   @Test
   @EnabledForJreRange(min = JRE.JAVA_11)
   public void testNormalChange() throws IOException, ParseException {
      final File secondVersion = buildSecondVersion();

      final ChangeManager changeManager = DependencyDetectorTestUtil.defaultChangeManager();

      final CommitIterator fakeIterator = new FakeFileIterator(DependencyTestConstants.CURRENT, Arrays.asList(secondVersion));

      final DependencyReader reader = DependencyDetectorTestUtil.readTwoVersions(changeManager, fakeIterator);

      System.out.println(reader.getDependencies());

      final TestSet testMe = DependencyDetectorTestUtil.findDependency(reader.getDependencies(), "defaultpackage.NormalDependency#executeThing", DependencyTestConstants.VERSION_1);
      final TestMethodCall testcase = testMe.getTestMethods().iterator().next();
      Assert.assertEquals("defaultpackage.TestMe", testcase.getClazz());
      Assert.assertEquals("testMe", testcase.getMethod());
   }

   private File buildSecondVersion() throws IOException {
      final File secondVersion = new File("target/dependency_change", "normal_change");
      FileUtils.copyDirectory(DependencyTestConstants.NORMAL_CHANGE, secondVersion);
      FileUtils.copyFile(new File(DependencyTestConstants.VERSIONS_FOLDER, "pom-11.xml"), new File(secondVersion, "pom.xml"));
      return secondVersion;
   }
}
