package in.workarounds.define.ui.activity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import edu.smu.tspell.wordnet.Synset;
import in.workarounds.define.R;
import in.workarounds.define.api.Constants;
import in.workarounds.define.file.unzip.UnzipHandler;
import in.workarounds.define.file.unzip.UnzipService;
import in.workarounds.define.helper.DownloadProgressThread;
import in.workarounds.define.helper.DownloadResolver;
import in.workarounds.define.ui.adapter.LivioListAdapter;
import in.workarounds.define.util.PrefUtils;
import in.workarounds.define.util.ViewUtils;
import in.workarounds.define.webviewDicts.livio.LivioDictionary;
import in.workarounds.define.webviewDicts.livio.LivioLanguages;
import in.workarounds.define.webviewDicts.livio.LivioModule;
import in.workarounds.define.wordnet.WordnetDictionary;
import in.workarounds.define.wordnet.WordnetModule;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

/**
 * Created by madki on 30/09/15.
 */
public class DictionariesActivity extends BaseActivity implements UnzipHandler.HandlerCallback, View.OnClickListener {
    /**
     * Messenger that delivers messages to UnzipService when bound
     */
    private Messenger mUnzipService;
    /**
     * messenger to be passed to the bound service
     */
    private Messenger mMessenger = new Messenger(new UnzipHandler(this));
    /**
     * flag to see if the activity is bound to the service
     */
    private boolean mBound;
    /**
     * Thread that listens and updates download progress
     */
    private DownloadProgressThread mThread;

    private static final int MY_PERMISSIONS_REQUEST_STORAGE = 101;// stricly below 255

    private boolean downloadPending = false;
    private ProgressBar unzipProgress;
    private ProgressBar downloadProgress;
    private TextView statusTv;
    private ListView livioList;
    private LivioListAdapter adapter;
    private ImageView downloadButton;
    private ImageView cancelButton;
    private AlertDialog permissionDialog;
    private static final String[] testWords = new String[]{"define", "pure", "heavy", "beat", "apple", "test"};
    @Inject
    WordnetDictionary wordnetDictionary;
    @Inject
    LivioDictionary livioDictionary;
    private CompositeSubscription subscriptions;

    @Override
    protected void onStart() {
        super.onStart();
        bindToUnzipService();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionaries);
        subscriptions = new CompositeSubscription();

        adapter = new LivioListAdapter(this);
        livioList = (ListView) findViewById(R.id.list_livio_dicts);

        View header = LayoutInflater.from(this).inflate(R.layout.list_livio_header, livioList, false);
        View footer = LayoutInflater.from(this).inflate(R.layout.list_livio_footer, livioList, false);

        livioList.addHeaderView(header);
        livioList.addFooterView(footer);

        livioList.setAdapter(adapter);

        statusTv = (TextView) header.findViewById(R.id.tv_progress_status);
        unzipProgress = (ProgressBar) header.findViewById(R.id.pb_unzip);
        downloadProgress = (ProgressBar) header.findViewById(R.id.pb_download);

        ViewUtils.setColorOfProgressBar(unzipProgress, ContextCompat.getColor(this, R.color.theme_primary));
        ViewUtils.setColorOfProgressBar(downloadProgress, ContextCompat.getColor(this, R.color.theme_primary));

        downloadButton = (ImageView) header.findViewById(R.id.btn_download_wordnet);
        cancelButton = (ImageView) header.findViewById(R.id.btn_cancel_download_wordnet);

        inject();

        View nextButton = findViewById(R.id.btn_next);
        if (!PrefUtils.getTutorialDone(this)) {
            nextButton.setVisibility(View.VISIBLE);
        } else {
            nextButton.setVisibility(View.GONE);
        }
        nextButton.setOnClickListener(this);

        mThread = DownloadResolver.setUpProgress(Constants.WORDNET, downloadProgress, statusTv, this);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void inject() {
        DaggerDictionaryComponent.builder().livioModule(new LivioModule())
                .wordnetModule(new WordnetModule()).build().inject(this);
    }

    private void setDictionaryFlags() {
        List<LivioLanguages.Language> languages = adapter.languages();
        for (int i = 0; i < languages.size(); i++) {
            subscriptions.add(getLivioSubscription(i, languages.get(i)));
        }
        subscriptions.add(getWordnetSubscription());
    }

    private Subscription getLivioSubscription(int position, LivioLanguages.Language language) {
        return livioDictionary.resultsObservable(testWords[0], language)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<String>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        adapter.setStatus(position, false);
                    }

                    @Override
                    public void onNext(String s) {
                        adapter.setStatus(position, true);
                    }
                });
    }

    private Subscription getWordnetSubscription() {
        return wordnetDictionary.resultsObservable(testWords[new Random().nextInt(5)])
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<Synset>>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        downloadButton.setImageResource(R.drawable.ic_download);
                        downloadButton.setColorFilter(ContextCompat.getColor(DictionariesActivity.this, R.color.theme_accent));
                        downloadButton.setOnClickListener(DictionariesActivity.this);
                        cancelButton.setOnClickListener(DictionariesActivity.this);
                        if (downloadPending) {
                            downloadPending = false;
                            initWordnetDownload();
                        }
                    }

                    @Override
                    public void onNext(List<Synset> synsets) {
                        if (downloadPending) {
                            downloadPending = false;
                        }
                        setWordnetTick();
                    }
                });
    }


    private void setWordnetTick() {
        downloadButton.setImageResource(R.drawable.ic_tick);
        downloadButton.setColorFilter(ContextCompat.getColor(DictionariesActivity.this, R.color.green));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindFromUnzipService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        unzipProgress.setVisibility(View.GONE);
        statusTv.setVisibility(View.GONE);

        setDictionaryFlags();
    }

    @Override
    protected void onPause() {
        super.onPause();
        subscriptions.clear();
    }

    @Override
    protected String getToolbarTitle() {
        return "Dictionaries";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mThread != null) {
            mThread.close();
        }
        if (permissionDialog != null) {
            permissionDialog.dismiss();
        }
    }

    /**
     * helper method to unbind from the UnzipService
     */
    private void unbindFromUnzipService() {
        // Unbind from the service
        if (mBound) {
            Message msg = Message.obtain(null, UnzipService.MSG_UNREGISTER);
            sendToUnzipService(msg);
            unbindService(mConnection);
            mBound = false;
        }
    }


    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mUnzipService = new Messenger(service);
            mBound = true;

            Message msg = Message.obtain(null, UnzipService.MSG_REGISTER, mMessenger);
            sendToUnzipService(msg);
        }

        public void onServiceDisconnected(ComponentName className) {
            mUnzipService = null;
            mBound = false;
        }
    };

    /**
     * helper method to bind to UnzipService
     */
    private void bindToUnzipService() {
        // Bind to the service
        bindService(new Intent(this, UnzipService.class), mConnection,
                Context.BIND_AUTO_CREATE);
    }

    /**
     * helper method to send message to unzip service
     *
     * @param message to be sent to UnzipService
     */
    private void sendToUnzipService(Message message) {
        if (!mBound) {
            Timber.e("Service not bound");
        } else {
            try {
                mUnzipService.send(message);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onUnzipProgressUpdate(int progress) {
        statusTv.setVisibility(View.VISIBLE);
        downloadProgress.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);

        if (progress > 0 && progress < 100) {
            statusTv.setText("Unzipping");
            unzipProgress.setVisibility(View.VISIBLE);
            unzipProgress.setProgress(progress);
        } else if (progress == 100) {
            unzipProgress.setVisibility(View.GONE);
            downloadProgress.setVisibility(View.GONE);
            statusTv.setVisibility(View.GONE);
            downloadButton.setVisibility(View.VISIBLE);
            setWordnetTick();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_download_wordnet:
                downloadClick();
                break;
            case R.id.btn_cancel_download_wordnet:
                cancelClick();
                break;
            case R.id.btn_install_livio:
                installLivio();
                break;
            case R.id.btn_next:
                next();
                break;
            default:
                Timber.e("No actionable define for this click");
        }
    }

    /**
     * function called on click of "Download Data" button calls
     * checkNetworkStatus and takes appropriate action
     */

    private void downloadClick() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_STORAGE);
        } else {
            initWordnetDownload();
        }
    }

    private void requestPermissionForStorageAfterNeverAllow() {
        permissionDialog = new AlertDialog.Builder(DictionariesActivity.this).create();
        permissionDialog.setTitle("Permissions");
        permissionDialog.setMessage("The app needs storage permission to download and save wordnet data offline.Click ok->permissions->storage enable");
        permissionDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        deniedPermissionForStorage();
                    }
                });
        permissionDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Ok",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.parse("package:" + getPackageName());
                        intent.setData(uri);
                        startActivity(intent);
                    }
                });
        permissionDialog.show();
    }

    private void deniedPermissionForStorage() {
        Toast.makeText(this, "Download failed! Permission Denied!", Toast.LENGTH_SHORT).show();
    }

    private void initWordnetDownload() {
        downloadButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
        statusTv.setVisibility(View.VISIBLE);
        DownloadResolver.startDownload(Constants.WORDNET, this);
        mThread = DownloadResolver.setUpProgress(Constants.WORDNET, downloadProgress, statusTv, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0]);
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    downloadPending = true;
                    setDictionaryFlags();
                } else if (!showRationale) { // when user clicked never allow before
                    requestPermissionForStorageAfterNeverAllow();
                } else {
                    deniedPermissionForStorage();
                }
            }
        }
    }

    /**
     * button's function to cancel ongoing download
     */
    public void cancelClick() {
        cancelButton.setVisibility(View.GONE);
        downloadButton.setVisibility(View.VISIBLE);

        downloadProgress.setVisibility(View.GONE);
        unzipProgress.setVisibility(View.GONE);
        DownloadResolver.cancelDownload(Constants.WORDNET, this);
        statusTv.post(new Runnable() {
            @Override
            public void run() {
                statusTv.setVisibility(View.GONE);
            }
        });
    }

    public void installLivio() {
        String livio = "livio.pack.lang.en_US";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + livio)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + livio)));
        }
    }

    public void next() {
        //Set the user has visited dictionaries screen
        PrefUtils.setDictionariesDone(true, this);
        Intent intent = new Intent(this, SplashActivity.class);
        startActivity(intent);
        finishOnStop = true;
    }
}
