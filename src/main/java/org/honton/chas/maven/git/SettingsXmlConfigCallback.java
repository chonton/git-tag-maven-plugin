package org.honton.chas.maven.git;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;

/**
 * Callback to create appropriate session factory based upons contents of ~/.m2/settings.xml
 */
@RequiredArgsConstructor
class SettingsXmlConfigCallback implements TransportConfigCallback {
  private final Log log;
  private final List<Server> servers;

  @Override
  public void configure(Transport transport) {
    if (transport instanceof SshTransport) {
      ((SshTransport) transport).setSshSessionFactory(new SettingsXmlFactory());
    }
  }

  /**
   * Session factory which creates available identities from maven settings containing private keys.
   */
  class SettingsXmlFactory extends JschConfigSessionFactory {
    @Override
    protected Session createSession(OpenSshConfig.Host hc, String user, String host, int port, FS fs) throws
      JSchException {
      JSch jSch = new JSch();
      for (Server server : servers) {
        String privateKey = server.getPrivateKey();
        if (privateKey != null) {
          log.debug(server.getId() + " is potential ssh connection");
          jSch.addIdentity(privateKey, server.getPassphrase());
        }
      }
      return jSch.getSession(user, host, port);
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
    }
  }
}
