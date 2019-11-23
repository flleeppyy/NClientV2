package com.dar.nclientv2.async;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.dar.nclientv2.GalleryActivity;
import com.dar.nclientv2.R;
import com.dar.nclientv2.api.components.Gallery;
import com.dar.nclientv2.settings.Global;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Request;

public class DownloadGallery extends IntentService {
    private Gallery gallery;
    private NotificationCompat.Builder notification;
    private final int notId;
    private int page=0;
    private NotificationManagerCompat notificationManager;
    public DownloadGallery(){
        super("Download Gallery");
        Log.d(Global.LOGTAG,"Starting download");
        notId=Global.getNotificationId();
    }
    private void downloadedPage(){
        notification.setProgress(gallery.getPageCount()-1,++page,false)
                .setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(getString(R.string.channel1_title_format,page,gallery.getPageCount())).bigText(gallery.getTitle()))
                .setContentText(getString(R.string.percentage_format,(page*100)/gallery.getPageCount()))
                .setContentTitle(getString(R.string.channel1_title));
        notificationManager.notify(getString(R.string.channel1_name),notId,notification.build());
    }
    private void onPreExecute() {
        //Crea la base della notifica
        Intent resultIntent = new Intent(this, GalleryActivity.class);
        resultIntent.putExtra(getPackageName()+".GALLERY",gallery);
        resultIntent.putExtra(getPackageName()+".INSTANTDOWNLOAD",true);
        PendingIntent resultPendingIntent=null;
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1){
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addNextIntentWithParentStack(resultIntent);
            resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notification=new NotificationCompat.Builder(getApplicationContext(), Global.CHANNEL_ID1);
        //notification.addAction(R.drawable.ic_close,"Stop",new PendingIntent.)
        notification.setOnlyAlertOnce(true)
                .setStyle(new NotificationCompat.BigTextStyle().setBigContentTitle(getString(R.string.channel1_title_format,0,gallery.getPageCount())).bigText(gallery.getTitle()))
                .setContentTitle(getString(R.string.channel1_title))
                .setContentText(getString(R.string.percentage_format,0))
                .setContentIntent(resultPendingIntent)
                .setProgress(gallery.getPageCount()-1,0,false)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)notification.setSmallIcon(R.drawable.ic_file);
        notificationManager.notify(getString(R.string.channel1_name),notId,notification.build());
    }
    private int a;
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        gallery=intent.getParcelableExtra(getPackageName()+".GALLERY");
        if(gallery==null)return;
        onPreExecute();
        Intent intent1=new Intent(this,DownloadGallery.class);
        intent1.setAction("stop");
        PendingIntent pStopSelf = PendingIntent.getService(this, 0, intent1, PendingIntent.FLAG_CANCEL_CURRENT);
        notification.addAction(R.drawable.ic_close,"Stop",pStopSelf);
        File folder=Global.findGalleryFolder(gallery.getId());
        if(folder==null){
            folder=new File(Global.DOWNLOADFOLDER,gallery.getSafeTitle());
            folder.mkdirs();
            File nomedia=new File(folder,".nomedia");
            try {
                nomedia.createNewFile();
                FileOutputStream ostream = new FileOutputStream(nomedia);
                ostream.write(Integer.toString(gallery.getId()).getBytes());
                ostream.flush();
                ostream.close();
            }catch (IOException e){
                Log.e(Global.LOGTAG, e.getLocalizedMessage(),e); }
        }
        System.gc();
        for(a=0;a<gallery.getPageCount();a++){
            final File x=new File(folder,("000"+(a+1)+"."+gallery.getPageExtension(a)).substring(Integer.toString(a+1).length()));
            if(!x.exists()){
                try{
                    downloadPage(Global.client.newCall(new Request.Builder().url(gallery.getPage(a)).build()).execute().body().byteStream(),x);
                    downloadedPage();
                }catch(IOException|NullPointerException e){
                    Log.e(Global.LOGTAG, e.getLocalizedMessage(),e);
                    a--;
                }
            }
            else downloadedPage();
        }
        onPostExecute();
    }
    private void downloadPage(InputStream stream,File file)throws IOException,NullPointerException{
        if(!file.getParentFile().exists()&&!file.getParentFile().mkdirs())return;
        if(!file.createNewFile())return;
        FileOutputStream ostream = new FileOutputStream(file);
        byte[] buffer = new byte[4 * 1024]; // or other buffer size
        int read;

        while ((read = stream.read(buffer)) != -1) {
            ostream.write(buffer, 0, read);
        }
        ostream.flush();
        ostream.close();
        stream.close();
    }
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if(intent!=null&&"stop".equals(intent.getAction())){
            a=999;
            Log.d(Global.LOGTAG,flags+","+startId);
            if(notification!=null) {
                notification.setStyle(null)
                        .setContentTitle(getString(R.string.cancelling)).
                        setContentText("").setProgress(0, 0, true);
                notificationManager.notify(getString(R.string.channel1_name), notId, notification.build());
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @SuppressLint("RestrictedApi")
    private void onPostExecute() {
        Log.d(Global.LOGTAG,"End reached");
        downloadedPage();
        notification.setProgress(0,0,false);
        if(Build.VERSION.SDK_INT>Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) notification.mActions.clear();
        notification.setStyle(null).setContentTitle(getString(a==1000?R.string.download_canceled :R.string.download_completed)).setContentText(gallery.getTitle()).setOnlyAlertOnce(false);
        notificationManager.notify(getString(R.string.channel1_name),notId,notification.build());
    }
}
