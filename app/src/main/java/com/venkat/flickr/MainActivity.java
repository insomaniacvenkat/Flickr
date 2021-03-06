package com.venkat.flickr;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.venkat.flickr.entity.SingleImage;
import com.venkat.flickr.fragments.DownloadFragment;
import com.venkat.flickr.fragments.HistoryFragment;
import com.venkat.flickr.fragments.PicturesFragment;
import com.venkat.flickr.fragments.TextFragment;
import com.venkat.flickr.gson.TopLevel;
import com.venkat.flickr.rest.RestClient;
import com.venkat.flickr.tasks.SaveToGallery;
import com.venkat.flickr.tasks.SaveToHistory;

import java.util.function.Consumer;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements
        PicturesFragment.OnPictureFragmentInteractionListener, DownloadFragment.OnDownloadFragmentInteractionListener,
        HistoryFragment.OnHistoryFragmentInteractionListener{

    private Disposable disposable;
    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String SEARCH_PREFERENCES = "search_content";
    final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 9078;
    LinearLayout moreButton;
    ImageButton searchButton;
    EditText searchText;
    TopLevel globalTopLevel;
    String prevSearch="gaga";

    String search;

    LinearLayout historyButton;
    ImageView moreImage;
    TextView moreText;

    PicturesFragment picturesFragment;
    TextFragment textFragment;
    DownloadFragment downloadFragment;
    HistoryFragment historyFragment;

    InputMethodManager inputManager;
    private ProgressBar spinner;

    Bitmap bitmapImage;

    SingleImage zoomImage;

    boolean isConnected;

    SaveToGallery saveGalleryTask;
    SaveToHistory saveHistoryTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        picturesFragment = new PicturesFragment();
        textFragment = new TextFragment();
        // Start activity with introductory fragment
        getSupportFragmentManager().beginTransaction()
                .add(R.id.frameLayout, textFragment)
                .commit();

        checkNetwork(true);

        spinner = findViewById(R.id.progressBar1);
        spinner.setVisibility(View.GONE);

        inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);

        searchButton = findViewById(R.id.searchButton);
        searchText = findViewById(R.id.searchText);
        searchButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                //search for given input

                searchAction();

            }
        });

        searchText.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                //set search text empty if user clicks on it (convenience)
                searchText.setText("");

            }
        });
        //search button from keyboard
        searchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    searchAction();
                    handled = true;
                }
                return handled;
            }
        });

        moreImage = findViewById(R.id.moreImage);
        moreText = findViewById(R.id.moreText);
        moreButton = findViewById(R.id.moreButton);
        moreButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                //initially disable button since activity has just started and user has not searched yet
                changMoreClick(false);
                //add more pictures to the bottom, user must scroll after clicking this
                if(picturesFragment!=null){
                    picturesFragment.addToArray();
                }


            }
        });
        moreButton.setVisibility(View.INVISIBLE);

        historyButton = findViewById(R.id.historyButton);
        historyButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {

                historyFragment = new HistoryFragment();
                Bundle bundle = new Bundle();
                bundle.putString("history", getSearchHistory());
                historyFragment.setArguments(bundle);
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.frameLayout, historyFragment)
                        .commit();
            }
        });

        changMoreClick(false);

    }

    void checkNetwork(boolean showDialog){
        ConnectivityManager cm =
                (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        if(!isConnected && showDialog){
            alertToConnect();
        }
    }

    void alertToConnect(){

        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle(getResources().getString(R.string.no_network));
        alertDialog.setMessage(getResources().getString(R.string.no_network_message));
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();

    }

    void searchAction(){

        //hide keyboard
        inputManager.hideSoftInputFromWindow((null == getCurrentFocus()) ? null : getCurrentFocus().getWindowToken(),
                InputMethodManager.HIDE_NOT_ALWAYS);
        search = searchText.getText().toString();
        print(search);
        print(prevSearch);
        if(!search.equals(prevSearch)){

            checkNetwork(false);

            if(!isConnected){
                alertToConnect();

            }else if(search.isEmpty()){

                Toast.makeText(getApplicationContext(), getResources().getString(R.string.empty_search)
                        , Toast.LENGTH_LONG).show();
            }
            else{
                //spinner.setVisibility(View.VISIBLE);
                //addToSearch(search);
                saveHistoryTask = new SaveToHistory(this,search,spinner);
                saveHistoryTask.execute();
                makeRestCall(search);
            }

            prevSearch = search;

        }else{
            //Displayed if user presses search again
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.same_search)+" "+prevSearch
                    , Toast.LENGTH_LONG).show();
        }



    }



    void changMoreClick(boolean b){

        moreButton.setClickable(b);
        moreButton.setEnabled(b);
        if(b){
            moreImage.setBackgroundResource(R.drawable.add);
            moreText.setTextColor(ContextCompat.getColor(this, R.color.flickrGray));
        }else{
            moreImage.setBackgroundResource(R.drawable.add_dis);
            moreText.setTextColor(ContextCompat.getColor(this, R.color.flickrBlue));
        }

    }


    void makeRestCall(final String searchContent){

        disposable = RestClient.getInstance()
                .getStarredRepos1(searchContent)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        new Consumer<TopLevel>() {
                            @Override
                            public void accept(TopLevel topLevel) throws Exception{
                                Log.i(TAG, "RxJava2: Response from server for toplevel ...");
//                                System.out.println("result from call: "+topLevel);
                                globalTopLevel = topLevel;
                                picturesFragment = new PicturesFragment();
                                new Thread(new Runnable() { @Override public void run() {
                                    Glide.get(getApplicationContext()).clearDiskCache();
                                } }).start();
                                if(topLevel.getStat().equals("ok")){
                                    if (topLevel.getPhotos().getTotal().equals("0")){
                                        Toast.makeText(getApplicationContext(),
                                                getResources().getString(R.string.not_found)+" "+searchContent
                                                , Toast.LENGTH_LONG).show();
                                        stopProgressBar();
                                    }else{
                                        getSupportFragmentManager().beginTransaction()
                                                .replace(R.id.frameLayout, picturesFragment)
                                                .commit();
                                    }
                                }else{
                                    Toast.makeText(getApplicationContext(),
                                            getResources().getString(R.string.wrong)
                                            , Toast.LENGTH_LONG).show();
                                    stopProgressBar();
                                }

                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable t) {
                                Log.i(TAG, "RxJava2, HTTP Error: " + t.getMessage());
                                Toast.makeText(getApplicationContext(),
                                        getResources().getString(R.string.connection_error)
                                        , Toast.LENGTH_LONG).show();
                                stopProgressBar();
                            }
                        }
                );

    }

    void print(String s){
        System.out.println(s);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
        Glide.get(MainActivity.this).clearMemory();
        if(saveGalleryTask !=null){//cancel asynctask
            saveGalleryTask.cancel(true);
        }
        if(saveHistoryTask !=null){//cancel asynctask
            saveHistoryTask.cancel(true);
        }

    }

    public void onFragmentInteraction(String message){
        if(message.equals("send toplevel")){
            if(picturesFragment!=null){
                picturesFragment.initialSendToFragment(globalTopLevel);
                moreButton.setVisibility(View.VISIBLE);
            }
        }
    }

    public void moreAtBottom(boolean value){

        changMoreClick(value);

    }

    public void stopProgressBar(){
        spinner.setVisibility(View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void killDownloadFragment(){

        if(downloadFragment!=null){
            getSupportFragmentManager().beginTransaction()
                    .remove(downloadFragment)
                    .commit();
        }
    }

    public void killHistoryFragment(){

        if(historyFragment!=null){
            getSupportFragmentManager().beginTransaction()
                    .remove(historyFragment)
                    .commit();
        }
    }

    public void startDownloadFragment(SingleImage image){

        zoomImage = image;
        downloadFragment = new DownloadFragment();
        Bundle bundle = new Bundle();
        bundle.putString("url", image.getImageUrl());
        bundle.putString("title", image.getTitle());

        downloadFragment.setArguments(bundle);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.frameLayout, downloadFragment)
                .commit();
    }

    public void saveImageToGallery(){

        Glide.with(this)
                .load(zoomImage.getImageUrl())
                .asBitmap()
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation)  {
                        print("Came here");
                        bitmapImage = resource;
                        saveImage(resource);

                    }
                });
    }

    private void saveImage(Bitmap image) {
        //String savedImagePath = null;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
        }else{

            saveGalleryTask = new SaveToGallery(this,image);
            saveGalleryTask.execute();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    saveImage(bitmapImage);
                } else {
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.permission_denied)
                            , Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    String getSearchHistory(){
        String search_history;
        SharedPreferences mPreferences = this.getSharedPreferences("search_history", Context.MODE_PRIVATE);
        search_history = mPreferences.getString(SEARCH_PREFERENCES, "flickr");
        return search_history;

    }


    void clearSearch(){
        String search_history = "flickr";
        SharedPreferences mPreferences = this.getSharedPreferences("search_history", Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putString(SEARCH_PREFERENCES,search_history);
        editor.commit();
    }

    public void searchFromHistory(String search){

        spinner.setVisibility(View.VISIBLE);
        makeRestCall(search);

    }

    public void clearFromHistory(){
        clearSearch();
    }

    public void startProgressBar(){
        spinner.setVisibility(View.VISIBLE);
    }

}
