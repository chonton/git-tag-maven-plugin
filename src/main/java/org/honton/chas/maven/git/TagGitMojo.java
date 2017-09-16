package org.honton.chas.maven.git;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.IOException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Server;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

/**
 * Tag current code with a tagName and message.  Optionally use a branch other than HEAD.
 * Unless skipPush is set, the annotated tag is pushed to the origin.
 */
@Mojo(name = "tag", defaultPhase = LifecyclePhase.DEPLOY)
public class TagGitMojo extends AbstractGitMojo {

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
    tagCommand.setName(tagName).setMessage(message == null ? "release " + tagName : message).call();
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
    if (!useUseDotSsh) {
      pushCommand.setTransportConfigCallback(new MavenTransportConfigCallback());
      pushCommand.setCredentialsProvider(new MavenSshCredentialsProvider()).call();
    } else {
      pushCommand.setCredentialsProvider(new MavenCredentialsProvider()).call();
    }
  }

  class MavenTransportConfigCallback implements TransportConfigCallback {
    @Override
    public void configure(Transport transport) {
      if (transport instanceof SshTransport) {
        ((SshTransport) transport).setSshSessionFactory(new MavenSshSessionFactory());
      }
    }
  }

  class MavenSshSessionFactory extends JschConfigSessionFactory {
    @Override
    protected Session createSession(OpenSshConfig.Host hc, String user, String host, int port,
      FS fs) throws JSchException {
      JSch jSch = new JSch();
      for (Server server : settings.getServers()) {
        String privateKey = server.getPrivateKey();
        if (privateKey != null) {
          getLog().debug(server.getId() + " is potential ssh connection");
          jSch.addIdentity(privateKey, server.getPassphrase());
        }
      }
      return jSch.getSession(user, host, port);
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
    }
  }

  class MavenCredentialsProvider extends CredentialsProvider {

    @Override
    public final boolean isInteractive() {
      return false;
    }

    @Override
    public final boolean supports(CredentialItem... credentialItems) {
      for (CredentialItem credentialItem : credentialItems) {
        if (!isSupported(credentialItem)) {
          return false;
        }
      }
      return true;
    }

    protected boolean isSupported(CredentialItem credentialItem) {
      return credentialItem instanceof CredentialItem.Username
        || credentialItem instanceof CredentialItem.Password;
    }

    @Override
    public final boolean get(URIish urIish, CredentialItem... credentialItems) {
      String id = getId(urIish);
      getLog().debug(urIish.toString() + " -> " + id);

      Server server = getServer(id);
      if (server == null) {
        getLog().error("No server matches " + id);
        return false;
      }
      for (CredentialItem credentialItem : credentialItems) {
        String failure = checkItem(server, credentialItem);
        if (failure != null) {
          throw new UnsupportedCredentialItem(urIish, failure);
        }
      }
      return true;
    }

    protected String checkItem(Server server, CredentialItem credentialItem) {
      if (credentialItem instanceof CredentialItem.Username) {
        ((CredentialItem.Username) credentialItem).setValue(server.getUsername());
        return null;
      }
      if (credentialItem instanceof CredentialItem.Password) {
        ((CredentialItem.Password) credentialItem).setValue(server.getPassword().toCharArray());
        return null;
      }
      return "Cannot support credential type " + credentialItem.getClass().getCanonicalName();
    }

    protected String getId(URIish urIish) {
      int port = urIish.getPort();
      if (-1 != port) {
        return urIish.getHost() + ':' + port;
      } else {
        return urIish.getHost();
      }
    }
  }

  class MavenSshCredentialsProvider extends MavenCredentialsProvider {

    protected boolean isSupported(CredentialItem credentialItem) {
      return credentialItem instanceof CredentialItem.YesNoType
        || super.isSupported(credentialItem);
    }

    protected String checkItem(Server server, CredentialItem credentialItem) {
      if (credentialItem instanceof CredentialItem.YesNoType) {
        getLog().debug(credentialItem.getPromptText());
        if (credentialItem.getPromptText().contains("RSA key fingerprint")) {
          ((CredentialItem.YesNoType) credentialItem).setValue(true);
          return null;
        }
        return "Cannot support credential with prompt " + credentialItem.getPromptText();
      }
      return super.checkItem(server, credentialItem);
    }

    protected String getId(URIish urIish) {
      int port = urIish.getPort();
      if (port == 22) {
        return urIish.getHost();
      }
      return urIish.getHost() + ':' + port;
    }
  }

}
