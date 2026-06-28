package com.miniredis.security;

import com.miniredis.config.ServerConfig;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * TlsConfig — initializes the SSLContext for TLS traffic.
 * Generates a self-signed PKCS12 keystore dynamically if none is provided.
 */
public class TlsConfig {

    public static SSLContext createSSLContext(ServerConfig config) throws Exception {
        String keystorePath = config.getTlsCertPath();
        String password = "changeit";

        if (keystorePath == null || keystorePath.isEmpty()) {
            keystorePath = config.getDataDir() + File.separator + "keystore.p12";
            File keystoreFile = new File(keystorePath);
            if (!keystoreFile.exists()) {
                System.out.println("[TLS] Generating self-signed certificate keystore at: " + keystorePath);
                generateSelfSignedKeystore(keystorePath, password);
            }
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }

    private static void generateSelfSignedKeystore(String path, String password) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-keyalg", "RSA",
                    "-keysize", "2048",
                    "-alias", "miniredis",
                    "-keystore", path,
                    "-storepass", password,
                    "-keypass", password,
                    "-dname", "CN=localhost, O=MiniRedis, C=US",
                    "-validity", "365",
                    "-deststoretype", "pkcs12"
            );
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            System.err.println("[TLS] Failed to generate self-signed keystore: " + e.getMessage());
        }
    }
}
