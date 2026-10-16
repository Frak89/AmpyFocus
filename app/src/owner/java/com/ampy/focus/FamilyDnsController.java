package com.ampy.focus;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opt-in family DNS-over-TLS policy for fully managed devices.
 * Uses an external DNS provider: requests may be processed by Cloudflare.
 * Never call blocking setGlobalPrivateDnsModeSpecifiedHost on the UI thread.
 */
public final class FamilyDnsController {
    private static final String FAMILY_HOST = "family.cloudflare-dns.com";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private FamilyDnsController() {}

    public static void update(Context c, boolean focused) {
        Context app = c.getApplicationContext();
        if (Build.VERSION.SDK_INT < 29) return;
        boolean enabled = FocusStore.prefs(app).getBoolean("family_dns_enabled", false);
        boolean applied = FocusStore.prefs(app).getBoolean("family_dns_applied", false);
        if (!applied && !(focused && enabled)) return;
        WORKER.execute(() -> applyWorker(app, focused && enabled));
    }

    private static void applyWorker(Context app, boolean enable) {
        DevicePolicyManager dpm = (DevicePolicyManager) app.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isDeviceOwnerApp(app.getPackageName())) return;
        ComponentName admin = new ComponentName(app, FocusAdminReceiver.class);
        SharedPreferences p = FocusStore.prefs(app);
        boolean applied = p.getBoolean("family_dns_applied", false);
        try {
            if (enable && !applied) {
                int mode = dpm.getGlobalPrivateDnsMode(admin);
                String host = dpm.getGlobalPrivateDnsHost(admin);
                // Android DPM has no public API to restore PRIVATE_DNS_MODE_OFF.
                if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_OFF
                        || mode == DevicePolicyManager.PRIVATE_DNS_MODE_UNKNOWN) {
                    p.edit().putString("family_dns_status",
                            "DNS Off/non noto: impossibile ripristinare automaticamente la configurazione.")
                            .apply();
                    return;
                }
                int result = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, FAMILY_HOST);
                if (result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                    p.edit().putBoolean("family_dns_applied", true)
                            .putInt("previous_dns_mode", mode)
                            .putString("previous_dns_host", host == null ? "" : host)
                            .putString("family_dns_status", "Filtro DNS famiglia attivo (Cloudflare).")
                            .apply();
                } else {
                    p.edit().putString("family_dns_status",
                            "Server DNS non raggiungibile; nessun filtro web attivato (" + result + ").")
                            .apply();
                }
            } else if (!enable && applied) {
                // Do not clobber manual DNS changes made while Focus was enabled.
                if (!FAMILY_HOST.equals(dpm.getGlobalPrivateDnsHost(admin))) {
                    p.edit().putBoolean("family_dns_applied", false)
                            .putString("family_dns_status", "DNS modificato esternamente: nessun ripristino.")
                            .apply();
                    return;
                }
                int mode = p.getInt("previous_dns_mode",
                        DevicePolicyManager.PRIVATE_DNS_MODE_UNKNOWN);
                int result;
                if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME) {
                    String host = p.getString("previous_dns_host", "");
                    if (host.isEmpty()) return;
                    result = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, host);
                } else if (mode == DevicePolicyManager.PRIVATE_DNS_MODE_OPPORTUNISTIC) {
                    result = dpm.setGlobalPrivateDnsModeOpportunistic(admin);
                } else {
                    p.edit().putString("family_dns_status", "Ripristino DNS non supportato").apply();
                    return;
                }
                if (result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                    p.edit().putBoolean("family_dns_applied", false)
                            .putString("family_dns_status", "DNS precedente ripristinato.").apply();
                } else {
                    p.edit().putString("family_dns_status", "Impossibile ripristinare DNS (" + result + ")").apply();
                }
            }
        } catch (RuntimeException e) {
            p.edit().putString("family_dns_status",
                    "Errore filtro DNS: " + e.getClass().getSimpleName()).apply();
        }
    }
}
