package com.seafile.seadroid;

import java.io.File;
import java.net.URISyntaxException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager.OnBackStackChangedListener;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.app.ActionBar;
import com.ipaulpro.afilechooser.utils.FileUtils;
import com.seafile.seadroid.account.Account;
import com.seafile.seadroid.data.DataManager;
import com.seafile.seadroid.data.SeafCachedFile;
import com.seafile.seadroid.data.SeafDirent;
import com.seafile.seadroid.data.SeafRepo;
import com.seafile.seadroid.data.DataManager.ProgressMonitor;
import com.seafile.seadroid.ui.CacheFragment;
import com.seafile.seadroid.ui.CacheFragment.OnCachedFileSelectedListener;
import com.seafile.seadroid.ui.FileFragment;
import com.seafile.seadroid.ui.ReposFragment;


public class BrowserActivity extends SherlockFragmentActivity 
        implements ReposFragment.OnFileSelectedListener, OnBackStackChangedListener, 
            OnCachedFileSelectedListener {
    
    private static final String DEBUG_TAG = "BrowserActivity";
    
    private Account account;
    NavContext navContext = null;
    DataManager dataManager = null;
    
    // private boolean twoPaneMode = false;
    ReposFragment reposFragment = null;
    CacheFragment cacheFragment = null;
    
    private String currentTab;
    private static final String LIBRARY_TAB = "libraries";
    private static final String CACHE_TAB = "cache";
    
    public DataManager getDataManager() {
        return dataManager;
    }

    public Account getAccount() {
        return account;
    }
    
    public NavContext getNavContext() {
        return navContext;
    }
    
    public class TabListener implements ActionBar.TabListener {

        private final String mTag;

        /** Constructor used each time a new tab is created.
          * @param activity  The host Activity, used to instantiate the fragment
          * @param tag  The identifier tag for the fragment
          * @param clz  The fragment's Class, used to instantiate the fragment
          */
        public TabListener(String tag) {
            mTag = tag;
        }

        @Override
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            currentTab = mTag;
            if (mTag.equals(LIBRARY_TAB)) {
                showReposFragment(ft);
            } else if (mTag.equals(CACHE_TAB)) {
                showCacheFragment(ft);
            }
        }

        @Override
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mTag.equals(LIBRARY_TAB)) {
                hideReposFragment(ft);
            } else if (mTag.equals(CACHE_TAB)) {
                hideCacheFragment(ft);
            }
        }

        @Override
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        
        // Get the message from the intent
        Intent intent = getIntent();
        String server = intent.getStringExtra("server");
        String email = intent.getStringExtra("email");
        String token = intent.getStringExtra("token");
        account = new Account(server, email, null, token);
        Log.d(DEBUG_TAG, "browser activity onCreate " + server + " " + email);
        
        dataManager = new DataManager(this, account);
        navContext = new NavContext();
        
        //setContentView(R.layout.seadroid_main);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(false);
        unsetRefreshing();
        
        int cTab = 0;

        if (savedInstanceState != null) {
            // fragment are saved during screen rotation, so do not need to create a new one
            reposFragment = (ReposFragment)
                    getSupportFragmentManager().findFragmentByTag("repos_fragment");
            cacheFragment = (CacheFragment)
                    getSupportFragmentManager().findFragmentByTag("cache_fragment");
            cTab = savedInstanceState.getInt("tab");
            
            String repo = savedInstanceState.getString("repo");
            String path = savedInstanceState.getString("path");
            String dirID = savedInstanceState.getString("dirID");
            if (repo != null) {
                navContext.setRepo(repo);
                navContext.setDir(path, dirID);
            }
        }
        
        Tab tab = actionBar.newTab()
                .setText(R.string.libraries)
                .setTabListener(new TabListener(LIBRARY_TAB));
        actionBar.addTab(tab);

        tab = actionBar.newTab()
            .setText(R.string.cached)
            .setTabListener(new TabListener(CACHE_TAB));
        actionBar.addTab(tab);

        actionBar.setSelectedNavigationItem(cTab);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        String server = intent.getStringExtra("server");
        String email = intent.getStringExtra("email");
        String token = intent.getStringExtra("token");
        account = new Account(server, email, null, token);

        String repoID = intent.getStringExtra("repoID");
        String path = intent.getStringExtra("path");
        String objectID = intent.getStringExtra("objectID");
        long size = intent.getLongExtra("size", 0);
        Log.d(DEBUG_TAG, "browser activity onNewIntent " + server + " " + email);
        Log.d(DEBUG_TAG, "repoID " + repoID + ":" + path + ":" + objectID);
        
        
        if (getSupportActionBar().getSelectedNavigationIndex() != 0) {
            //
        } else {
            //
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("tab", getSupportActionBar().getSelectedNavigationIndex());
        outState.putString("repo", navContext.getRepo());
        outState.putString("path", navContext.getDirPath());
        outState.putString("dirID", navContext.getDirID());
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.browser_menu, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuDeleteCache = menu.findItem(R.id.delete_cache);
        MenuItem menuUpload = menu.findItem(R.id.upload);
        MenuItem menuRefresh = menu.findItem(R.id.refresh);
        
        if (currentTab.equals(CACHE_TAB) && cacheFragment.isItemSelected()) {
            Log.d(DEBUG_TAG, "refreshMenu set visible");
            menuDeleteCache.setVisible(true);
        } else
            menuDeleteCache.setVisible(false);
        
        if (currentTab.equals(LIBRARY_TAB) && navContext.inRepo())
            menuUpload.setEnabled(true);
        else
            menuUpload.setEnabled(false);
        
        if (currentTab.equals(LIBRARY_TAB))
            menuRefresh.setVisible(true);
        else
            menuRefresh.setVisible(false);
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            if (navContext.isRepoRoot()) {
                navContext.setRepo(null);
            } else {
                String parentPath = Utils
                        .getParentPath(navContext.getDirPath());
                navContext.setDir(parentPath, null);
            }
            reposFragment.refreshView();

            return true;
        case R.id.delete_cache:
            cacheFragment.deleteSelectedCacheItems();
            return true;
        case R.id.upload:
            pickFile();
            return true;
        case R.id.refresh:
            reposFragment.refreshView();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showReposFragment(FragmentTransaction ft) {
        //Log.d(DEBUG_TAG, "showReposFragment");
        
        if (reposFragment == null) {
            reposFragment = new ReposFragment();
            ft.add(android.R.id.content, reposFragment, "repos_fragment");
        } else {
            Log.d(DEBUG_TAG, "Attach reposFragment");
            ft.attach(reposFragment);
        }
    }
    
    private void hideReposFragment(FragmentTransaction ft) {
        //Log.d(DEBUG_TAG, "hideReposFragment");
        ft.detach(reposFragment);
    }
    
    
    private void showCacheFragment(FragmentTransaction ft) {
        //Log.d(DEBUG_TAG, "showCacheFragment");
        if (cacheFragment == null) {
            cacheFragment = new CacheFragment();
            ft.add(android.R.id.content, cacheFragment, "cache_fragment");
        } else {
            ft.attach(cacheFragment);
        }
    }
    
    private void hideCacheFragment(FragmentTransaction ft) {
        //Log.d(DEBUG_TAG, "hideCacheFragment");
        ft.detach(cacheFragment);
    }

    public void setRefreshing() {
        setSupportProgressBarIndeterminateVisibility(Boolean.TRUE);
    }
    
    public void unsetRefreshing() {
        setSupportProgressBarIndeterminateVisibility(Boolean.FALSE);
    }
    
    public void showToast(CharSequence msg) {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
    
    public void enableUpButton() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    
    public void disableUpButton() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }
    
    /***********  Start other activity  ***************/
    
    void startFileActivity(String repoID, String path, String fileID, long size) {
        Intent intent = new Intent(this, FileActivity.class);
        intent.putExtra("server", account.server);
        intent.putExtra("email", account.email);
        intent.putExtra("token", account.token);
        intent.putExtra("repoID", repoID);
        intent.putExtra("path", path);
        intent.putExtra("fileID", fileID);
        intent.putExtra("size", size);
        startActivity(intent);
    }
    
    public static final int PICK_FILE_REQUEST = 1;
    
    void pickFile() {
        Intent target = FileUtils.createGetContentIntent();
        Intent intent = Intent.createChooser(target, getString(R.string.choose_file));
        try {
            startActivityForResult(intent, PICK_FILE_REQUEST);
        } catch (ActivityNotFoundException e) {
            // The reason for the existence of aFileChooser
        }
    }
    
    private String getImageRealPathFromURI(Uri contentURI) {
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        cursor.moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        String result = cursor.getString(idx);
        cursor.close();
        return result;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (!Utils.isNetworkOn(this)) {
                    showToast("Network is not connected");
                    return;
                }
                
                Uri uri = data.getData();
                String path;
                try {
                    path = FileUtils.getPath(this, uri);
                } catch (URISyntaxException e) {
                    return;
                }
                showToast("Uploading " + Utils.fileNameFromPath(path));
                new UploadTask(navContext.getRepo(), navContext.getDirPath(), path).execute();
            }
        }
    }
    
    /***************  Navigation *************/
    
    
    // File selected in repos fragment
    public void onFileSelected(String repoID, String path, SeafDirent dirent) {
        File file = DataManager.getFileForFileCache(path, dirent.id);
        if (file.exists()) {
            showFile(repoID, path, dirent.id, dirent.size);
        } else
            startFileActivity(repoID, path, dirent.id, dirent.size);
    }
    
    @Override
    public void onCachedFileSelected(SeafCachedFile item) {
        showFile(item.repo, item.path, item.fileID, item.getSize());
    }
    
    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() != 0) {
            getSupportFragmentManager().popBackStack();
            return;
        }
        

        if (currentTab.equals("libraries")) {
            if (navContext.inRepo()) {
                if (navContext.isRepoRoot()) {
                    navContext.setRepo(null);
                } else {
                    String parentPath = Utils.getParentPath(navContext
                            .getDirPath());
                    navContext.setDir(parentPath, null);
                }
                reposFragment.refreshView();
            } else
                // back to StartActivity
                super.onBackPressed();
        } else if (currentTab.equals("cache")) {
            super.onBackPressed();
        } else
            super.onBackPressed();
    }

    @Override
    public void onBackStackChanged() {    
    }

    
    /************** Button clicks **************/
    
    // Open file button click in file fragment
    public void onOpenFileClick(View target) {       

        FileFragment fileFragment = (FileFragment)
                getSupportFragmentManager().findFragmentByTag("file_fragment");
        if (fileFragment != null && fileFragment.isVisible()) {
            fileFragment.openFile();
        }
    }
    
    public void onCancelDownloadClick(View target) {
        FileFragment fileFragment = (FileFragment)
                getSupportFragmentManager().findFragmentByTag("file_fragment");
        if (fileFragment != null && fileFragment.isVisible()) {
            fileFragment.cancelDownload();
        }
    }
    
    public void onRefreshClick(View target) {
        reposFragment.refreshView();
    }
    
    private void startMarkdownActivity(String repoID, String path, String fileID) {
        Intent intent = new Intent(this, MarkdownActivity.class);
        intent.putExtra("repoID", repoID);
        intent.putExtra("path", path);
        intent.putExtra("fileID", fileID);
        startActivity(intent);
    }
    
    private void showFile(String repoID, String path, String fileID, long size) {
        File file = DataManager.getFileForFileCache(path, fileID);
        String name = file.getName();
        String suffix = name.substring(name.lastIndexOf('.') + 1);
        
        if (suffix.length() == 0) {
            showToast(getString(R.string.unknown_file_type));
            return;
        }
        
        if (suffix.endsWith("md") || suffix.endsWith("markdown")) {
            startMarkdownActivity(repoID, path, fileID);
            return;
        }

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix);
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(file.getAbsolutePath()));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open.setAction(android.content.Intent.ACTION_VIEW);
        open.setDataAndType((Uri.fromFile(file)), mime);
        try {
            startActivity(open);
        } catch (ActivityNotFoundException e) {
            showToast(getString(R.string.activity_not_found));
        }
    }
    
    private int notificationID = 0;
    Notification notification;
    NotificationManager notificationManager;
    
    private class UploadTask extends AsyncTask<Void, Integer, Void> {

        public static final int showProgressThreshold = 50 * 1024;
        
        SeafException err = null;
        String myRepoID;
        String myDir;
        String myPath;
        long mySize;
        
        private int myNtID;
        
        public UploadTask(String repoID, String dir, String filePath) {
            this.myRepoID = repoID;
            this.myDir = dir;
            this.myPath = filePath;
            File f = new File(filePath);
            mySize = f.length();
            err = null;
        }
        
        @Override
        protected void onPreExecute() {
            if (mySize <= showProgressThreshold)
                return;
            myNtID = ++notificationID;
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Intent notificationIntent = new Intent(BrowserActivity.this, BrowserActivity.class);
            
            Account account = dataManager.getAccount();
            notificationIntent.putExtra("server", account.server);
            notificationIntent.putExtra("email", account.email);
            notificationIntent.putExtra("token", account.token);
            notificationIntent.putExtra("repoID", myRepoID);
            notificationIntent.putExtra("path", myDir);
            
            PendingIntent intent = PendingIntent.getActivity(BrowserActivity.this, myNtID, notificationIntent, 0);

            notification = new Notification(R.drawable.ic_stat_upload, "", System.currentTimeMillis());
            notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT;
            notification.contentView = new RemoteViews(getApplicationContext().getPackageName(),
                    R.layout.download_progress);
            notification.contentView.setCharSequence(R.id.tv_download_title, "setText",
                    Utils.fileNameFromPath(myPath));
            notification.contentIntent = intent;
            
            notification.contentView.setProgressBar(R.id.pb_download_progressbar,
                    (int)mySize, 0, false);
            notificationManager.notify(myNtID, notification);
        }
        
        @Override
        protected Void doInBackground(Void... params) {
            try {
                dataManager.uploadFile(myRepoID, myDir, myPath,  new FileUploadMonitor(this));
            } catch (SeafException e) {
                err = e;
            }
            return null;
        }
        
        @Override
        protected void onProgressUpdate(Integer... values) {
            int progress = values[0];
            notification.contentView.setProgressBar(R.id.pb_download_progressbar,
                    (int)mySize, progress, false);
            notificationManager.notify(myNtID, notification);
        }

        class FileUploadMonitor implements ProgressMonitor {
            UploadTask task;
            
            public FileUploadMonitor(UploadTask task) {
                this.task = task;
            }
            
            @Override
            public void onProgressNotify(long total) {
                publishProgress((int)total);
            }
            
            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(Void v) {            
            if (mySize > showProgressThreshold)
                notificationManager.cancel(myNtID);
            
            if (err != null) {
                showToast("Upload failed");
                return;
            }
            
            dataManager.invalidateCache(myRepoID, myDir);
            if (currentTab.equals(LIBRARY_TAB)
                    && myRepoID.equals(navContext.getRepo())
                    && myDir.equals(navContext.getDirPath()))
                reposFragment.refreshView();
        }

        
    }
}