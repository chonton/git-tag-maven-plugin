package org.honton.chas.maven.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.honton.chas.maven.git.TagGit.Configuration;

/**
 * Tag current code with a tagName and message.  Optionally use a branch other than HEAD.
 * Unless skipPush is set, the annotated tag is pushed to the origin.
 */
@Mojo(name = "tag", defaultPhase = LifecyclePhase.DEPLOY)
public class TagGitMojo extends AbstractMojo {

  @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
  private List<MavenProject> reactorProjects;

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

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
    try {
      List<Object> queue = getTagQueue();
      synchronized (queue) {
        queue.add(createCfg());
        if (queue.size() == reactorProjects.size()) {
          dequeueGitWorkspaces(queue);
        }
      }
    } catch (MojoExecutionException mee) {
      throw mee;
    } catch (MojoFailureException mfe) {
      throw mfe;
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private List<Object> getTagQueue() {
    Properties projectProperties = session.getUserProperties();

    // Plugin instances may be in different Classworlds if they are loaded in different modules
    // containing different extensions.  The plugin cannot rely on static variables, only injected
    // or session shared variables
    synchronized (projectProperties) {
      String propertyKey = getClass().getCanonicalName();
      List<Object> reqs = (List) projectProperties.get(propertyKey);
      if (reqs == null) {
        reqs = new ArrayList<>(reactorProjects.size());
        projectProperties.put(propertyKey, reqs);
      }
      return reqs;
    }
  }

  private TagGit.Configuration createCfg() {
    if (skip) {
      getLog().info("skipping git-tag execution");
      return null;
    } else {
      File gitDir = new FileRepositoryBuilder().findGitDir(baseDir).getGitDir();
      return new TagGit.Configuration(gitDir, branch, remote, tagName, message, skipPush,
        useUseDotSsh);
    }
  }

  public void dequeueGitWorkspaces(List<Object> queue) throws Exception {
    ConcurrentMap<String, TagGit.Configuration> work = new ConcurrentHashMap<>();
    for (Object q : queue) {
      if (q == null) {
        continue;
      }

      TagGit.Configuration cfg = CastHelper.cast(TagGit.Configuration.class, q);
      String key = cfg.getGitDir().getCanonicalPath();
      Configuration prior = work.putIfAbsent(key, cfg);
      if (prior != null && prior.equals(cfg)) {
        getLog().warn("Configurations for " + key + " are not consistent within reactor");
      }
    }

    for (TagGit.Configuration entry : work.values()) {
      TagGit tagGit = new TagGit(entry);
      tagGit.tagAndPush(getLog(), settings.getServers());
    }
  }
}
