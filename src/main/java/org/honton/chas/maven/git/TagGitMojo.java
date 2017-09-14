package org.honton.chas.maven.git;

import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 *
 */
@Mojo(name = "tag", defaultPhase = LifecyclePhase.DEPLOY)
public class TagGitMojo extends AbstractGitMojo {

  /**
   * Message for the tag
   */
  @Parameter(required = true, property = "git.tag")
  private String tag;

  /**
   * Message for the tag, defaults to "release ${tag}"
   */
  @Parameter(property = "git.message")
  private String message;

  /**
   * Skip pushing tag to remote
   */
  @Parameter(defaultValue = "false", property = "git.skipPush")
  private boolean skipPush;

  @Override
  protected void doExecute(Repository repository) throws Exception {
    try (Git git = new Git(repository)) {
      tag(git);
      if (!skipPush) {
        push(git);
      }
    }
  }

  private void tag(Git git) throws GitAPIException, IOException {
    TagCommand tagCommand = git.tag().setAnnotated(true);
    if (branch != null) {
      tagCommand.setObjectId(getObjectId(git));
    }
    tagCommand.setName(tag).setMessage(message==null ? "release " + tag : message).call();
  }

  private RevObject getObjectId(Git git) throws IOException {
    Repository repository = git.getRepository();
    ObjectId objectId = repository.findRef(branch).getObjectId();
    return new RevWalk(repository).parseAny(objectId);
  }

  private void push(Git git) throws GitAPIException {
    PushCommand pushCommand = git.push().setPushTags();
    if (remote != null) {
      pushCommand.setRemote(remote);
    }
    pushCommand.setCredentialsProvider(new MavenCredentialsProvider()).call();
  }

  class MavenCredentialsProvider extends CredentialsProvider {
    @Override
    public boolean isInteractive() {
      return false;
    }

    @Override
    public boolean supports(CredentialItem... credentialItems) {
      for (CredentialItem credentialItem : credentialItems) {
        if (credentialItem instanceof CredentialItem.Username
          || credentialItem instanceof CredentialItem.Password) {
          continue;
        }
        return false;
      }
      return true;
    }

    @Override
    public boolean get(URIish urIish, CredentialItem... credentialItems) {
      String id = getId(urIish);
      getLog().debug(urIish.toString() + " -> " + id);

      Server server = getServer(id);
      for (CredentialItem credentialItem : credentialItems) {
        if (credentialItem instanceof CredentialItem.Username) {
          ((CredentialItem.Username) credentialItem).setValue(server.getUsername());
        } else if (credentialItem instanceof CredentialItem.Password) {
          ((CredentialItem.Password) credentialItem).setValue(server.getPassword().toCharArray());
        } else {
          throw new UnsupportedCredentialItem(urIish, credentialItem.getClass().getCanonicalName());
        }
      }
      return true;
    }

    private String getId(URIish urIish) {
      int port = urIish.getPort();
      if (-1 != port) {
        return urIish.getHost() + ':' + port;
      } else {
        return urIish.getHost();
      }
    }
  }
}
