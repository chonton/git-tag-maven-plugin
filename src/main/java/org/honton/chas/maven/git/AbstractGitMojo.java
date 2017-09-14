package org.honton.chas.maven.git;

import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

/**
 *
 */
public abstract class AbstractGitMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  private MavenProject mavenProject;

  @Parameter(defaultValue = "${settings}", readonly = true)
  private Settings settings;

  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  private File baseDir;

  /**
   * Skip executing this plugin
   */
  @Parameter(defaultValue = "false", property = "git.skip")
  private boolean skip;

  /**
   * Git branch (defaults to HEAD branch)
   */
  @Parameter(property = "git.branch")
  protected String branch;

  /**
   * Git Remote URI
   */
  @Parameter(property = "git.remote")
  protected String remote;

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("skipping exists execution");
      return;
    }
    try {
      try (Repository repository = new FileRepositoryBuilder().findGitDir(baseDir).build()) {
        doExecute(repository);
      }
    } catch (MojoExecutionException mee) {
      throw mee;
    } catch (MojoFailureException mfe) {
      throw mfe;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  protected Server getServer(String id) {
    return settings.getServer(id);
  }

  protected abstract void doExecute(Repository repository) throws Exception;
}
