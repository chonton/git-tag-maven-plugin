package org.honton.chas.maven.git;

import com.google.common.base.Function;
import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Provide credentials from ~/.m2/settings.xml
 */
@RequiredArgsConstructor
class SettingsXmlCredentialsProvider extends CredentialsProvider {
  protected final Log log;
  protected final Function<String,Server> servers;

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
    log.debug(urIish.toString() + " -> " + id);

    Server server = servers.apply(id);
    if (server == null) {
      log.error("No server matches " + id);
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
      String serverUsername = server.getUsername();
      if(serverUsername != null) {
        ((CredentialItem.Username) credentialItem).setValue(serverUsername);
      }
      else {
        log.info("No username specified");
      }
      return null;
    }
    if (credentialItem instanceof CredentialItem.Password) {
      String serverPassword = server.getPassword();
      if(serverPassword != null) {
        ((CredentialItem.Password) credentialItem).setValue(serverPassword.toCharArray());
      }
      else {
        log.info("No password specified");
      }
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
