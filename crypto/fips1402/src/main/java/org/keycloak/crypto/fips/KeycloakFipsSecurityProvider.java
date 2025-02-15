package org.keycloak.crypto.fips;

import static org.bouncycastle.crypto.CryptoServicesRegistrar.isInApprovedOnlyMode;

import java.lang.reflect.Method;
import java.security.Provider;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.jboss.logging.Logger;

/**
 * Security provider to workaround usage of potentially unsecured algorithms by 3rd party dependencies.
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class KeycloakFipsSecurityProvider extends Provider {

    protected static final Logger logger = Logger.getLogger(KeycloakFipsSecurityProvider.class);

    private final BouncyCastleFipsProvider bcFipsProvider;

    public KeycloakFipsSecurityProvider(BouncyCastleFipsProvider bcFipsProvider) {
        super("KC(" +
                bcFipsProvider.toString() +
                (isInApprovedOnlyMode() ? " Approved Mode" : "") +
                (isSystemFipsEnabled() ? " FIPS-enabled JVM" : "") +
                ")", 1, "Keycloak pseudo provider");
        this.bcFipsProvider = bcFipsProvider;
    }

    @Override
    public synchronized final Service getService(String type, String algorithm) {
        // Using 'SecureRandom.getInstance("SHA1PRNG")' will delegate to BCFIPS DEFAULT provider instead of returning SecureRandom based on potentially unsecure SHA1PRNG
        if ("SHA1PRNG".equals(algorithm) && "SecureRandom".equals(type)) {
            logger.debug("Returning DEFAULT algorithm of BCFIPS provider instead of SHA1PRNG");
            return this.bcFipsProvider.getService("SecureRandom", "DEFAULT");
        } else {
            return null;
        }
    }

    private static boolean isSystemFipsEnabled() {
        Method isSystemFipsEnabled = null;

        try {
            Class<?> securityConfigurator = KeycloakFipsSecurityProvider.class.getClassLoader().loadClass("java.security.SystemConfigurator");
            isSystemFipsEnabled = securityConfigurator.getDeclaredMethod("isSystemFipsEnabled");
            isSystemFipsEnabled.setAccessible(true);
            return (boolean) isSystemFipsEnabled.invoke(null);
        } catch (Throwable ignore) {
            logger.debug("Could not detect if FIPS is enabled from the host");
        } finally {
            if (isSystemFipsEnabled != null) {
                isSystemFipsEnabled.setAccessible(false);
            }
        }

        return false;
    }
}
