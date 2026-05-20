package com.antisahir.app;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectionActivity extends AppCompatActivity {

    private AppAdapter adapter;
    private List<AppInfo> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewApps);
        Button btnSave            = findViewById(R.id.btnSaveApps);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        appList = loadInstalledApps();
        adapter = new AppAdapter(appList);
        recyclerView.setAdapter(adapter);

        btnSave.setOnClickListener(v -> saveSelection());
    }

    private List<AppInfo> loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

        Set<String> alreadyBlocked = SleepManager.getBlockedApps(this);

        // Packages we never allow blocking (our own app + system essentials)
        String ownPkg = getPackageName();

        List<AppInfo> result = new ArrayList<>();
        for (ApplicationInfo info : installedApps) {
            // Skip our own app
            if (info.packageName.equals(ownPkg)) continue;

            // Skip pure system apps (keep system apps that user deliberately installed)
            boolean isSystemApp = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean isUpdatedSystem = (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (isSystemApp && !isUpdatedSystem) continue;

            // Skip known launchers / settings (never let them be blocked)
            if (info.packageName.equals("com.android.settings")) continue;
            if (info.packageName.contains("launcher") || info.packageName.contains("home")) continue;

            String label = pm.getApplicationLabel(info).toString();
            boolean selected = alreadyBlocked.contains(info.packageName);
            result.add(new AppInfo(label, info.packageName, selected));
        }

        // Sort alphabetically
        Collections.sort(result, (a, b) -> a.getAppName().compareToIgnoreCase(b.getAppName()));
        return result;
    }

    private void saveSelection() {
        Set<String> selected = new HashSet<>();
        for (AppInfo app : appList) {
            if (app.isSelected()) {
                selected.add(app.getPackageName());
            }
        }
        SleepManager.saveBlockedApps(this, selected);
        Toast.makeText(this,
                "✅ تم حفظ " + selected.size() + " تطبيق للحجب",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
