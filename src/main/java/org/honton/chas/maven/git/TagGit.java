package org.honton.chas.maven.git;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;

@RequiredArgsConstructor
class TagGit {

  @Value
  @EqualsAndHashCode
  static class Configuration implements Serializable {
    File gitDir;
    String branch;
    String remote;
    String tagName;
    String message;
    boolean skipPush;
    boolean skipSnapshots;
    boolean useUseDotSsh;
  }

  final private Configuration cfg;
  private Log log;
  private List<Server> servers;
  private Function<String, Server> serverAccess;
  private String remoteUrl;

  public void tagAndPush(final Log log, final List<Server> servers)
    throws IOException, GitAPIException {
    this.log = log;
    this.servers = servers;

    if (cfg.getTagName().endsWith("SNAPSHOT") && cfg.isSkipSnapshots()) {
        log.info("Tagname '" + cfg.getTagName() + "' is recognized as SNAPSHOT and skipSnapshots is set to true. Tagging not evaluated");
        return;
    }

    serverAccess = new Function<String, Server>() {
      @Override
      public Server apply(final String id) {
        return Iterables.find(servers, new Predicate<Server>() {
          @Override
          public boolean apply(Server server) {
            return server.getId().equals(id);
          }
        }, null);
      }
    };
    try (Repository repository = new FileRepositoryBuilder().setGitDir(cfg.getGitDir()).build()) {
      remoteUrl = repository.getConfig().getString("remote", cfg.getRemote(), "url");
      log.debug(cfg.getRemote() + " url: " + remoteUrl);
      try (Git git = new Git(repository)) {
        tag(git);
        if (!cfg.isSkipPush()) {
          push(git);
        }
      }
    }
  }

  private void tag(Git git) throws GitAPIException, IOException {
    TagCommand tagCommand = git.tag().setAnnotated(true);
    if (cfg.getBranch() != null) {
      log.info("branch: " + cfg.getBranch() );
      tagCommand.setObjectId(getObjectId(git));
    }
    String message = cfg.getMessage() == null ? "release " + cfg.getTagName() : cfg.getMessage();
    log.info("tag name: " + cfg.getTagName() + ", message: " + message);

    tagCommand.setName(cfg.getTagName())
      .setMessage(message)
      .call();
  }

  private RevObject getObjectId(Git git) throws IOException {
    Repository repository = git.getRepository();
    ObjectId objectId = repository.findRef(cfg.getBranch()).getObjectId();
    return new RevWalk(repository).parseAny(objectId);
  }

  private void push(Git git) throws GitAPIException {
    PushCommand pushCommand = git.push().setPushTags();

    log.info("pushing tag to " + cfg.getRemote());
    pushCommand.setRemote(cfg.getRemote());

    if (!cfg.isUseUseDotSsh()) {
      pushCommand.setTransportConfigCallback(new SettingsXmlConfigCallback(log, servers));
      pushCommand.setCredentialsProvider(new SshCredentialsProvider(log, serverAccess));
    } else {
      pushCommand.setCredentialsProvider(new SettingsXmlCredentialsProvider(log, serverAccess));
    }
    for (PushResult resp : pushCommand.call()) {
      for (RemoteRefUpdate rru : resp.getRemoteUpdates()) {
        Status status = rru.getStatus();
        if (status != Status.OK && status != Status.UP_TO_DATE) {
          throw new PatchApplyException(
            "remote url: " + remoteUrl + ", status: " + status + ", message: " + rru.getMessage());
        }
      }
    }
  }
}
