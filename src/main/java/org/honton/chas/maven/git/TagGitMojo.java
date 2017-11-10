package org.honton.chas.maven.git;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

/**
 * Tag current code with a tagName and message.  Optionally use a branch other than HEAD.
 * Unless skipPush is set, the annotated tag is pushed to the origin.
 */
@Mojo(name = "tag", defaultPhase = LifecyclePhase.DEPLOY)
public class TagGitMojo extends AbstractMojo {

  private static final AtomicInteger READY_PROJECTS_COUNTER = new AtomicInteger();
  private static final ConcurrentHashMap<String, TagGit> GIT_REPOSITORIES = new ConcurrentHashMap();

  @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
  private List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${settings}", readonly = true)
  protected Settings settings;

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
  private String branch;

  /**
   * Git Remote repository name
   */
  @Parameter(defaultValue = "origin", property = "git.remote")
  private String remote;

  /**
   * Message for the tag
   */
  @Parameter(required = true, property = "git.tagName")
  private String tagName;

  /**
   * Message for the tag, defaults to "release ${tag}"
   */
  @Parameter(property = "git.message")
  private String message;

  /**
   * Skip pushing tag to remote.
   */
  @Parameter(defaultValue = "false", property = "git.skipPush")
  private boolean skipPush;

  /**
   * Use the contents of the ~/.ssh directory instead of ~/.m2/settings.xml to configure ssh
   * connections.
   */
  @Parameter(defaultValue = "false", property = "git.use.ssh")
  protected boolean useUseDotSsh;

  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("skipping git execution");
      return;
    }

    try {
      addGitWorkspace(baseDir);
      if (READY_PROJECTS_COUNTER.incrementAndGet() == reactorProjects.size()) {
        iterateGitWorkspaces();
      }
    } catch (MojoExecutionException mee) {
      throw mee;
    } catch (MojoFailureException mfe) {
      throw mfe;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void addGitWorkspace(File baseDir) throws Exception {
    TagGit value = new TagGit(branch, remote, tagName, message, skipPush, useUseDotSsh);
    TagGit prior = GIT_REPOSITORIES.putIfAbsent(value.createKey(baseDir), value);
    if (prior != null && !prior.equals(value)) {
      throw new MojoExecutionException("mismatch in branch or remote");
    }
  }

  private void iterateGitWorkspaces() throws Exception {
    for (TagGit entry : GIT_REPOSITORIES.values()) {
      entry.tagAndPush(getLog(), settings.getServers());
    }
  }
}
