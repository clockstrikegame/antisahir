package com.antisahir.app;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context,
                "مدير الجهاز مُفعَّل ✅ — لا يمكن حذف التطبيق قبل انتهاء البرنامج",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "⚠️ تعطيل مدير الجهاز سيسمح بحذف التطبيق. هل أنت متأكد؟";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context,
                "تم تعطيل مدير الجهاز",
                Toast.LENGTH_SHORT).show();
    }
}
