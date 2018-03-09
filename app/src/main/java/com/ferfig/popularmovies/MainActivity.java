package com.ferfig.popularmovies;

import android.support.v7.app.AppCompatActivity;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.OrientationHelper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ferfig.popularmovies.model.MovieData;
import com.ferfig.popularmovies.model.MoviesContract;
import com.ferfig.popularmovies.utils.Json;
import com.ferfig.popularmovies.utils.Network;
import com.ferfig.popularmovies.utils.UrlUtils;
import com.ferfig.popularmovies.utils.Utils;

import java.net.URL;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements LoaderCallbacks<String>,
        SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final int MOVIEDB_LOADER_ID = 26;
    private static final int LOCALDB_LOADER_ID = 27;

    private static final int ID_FOR_ACTIVITY_RESULT = 9;

    private ArrayList<MovieData> mMoviesList;
    private int mCurrentSortOrder;

    @BindView(R.id.rvMainRecyclerView) RecyclerView mMainRecyclerView;
    @BindView(R.id.pbProgress) ProgressBar mProgressBar;
    @BindView(R.id.tvErrorMessage) TextView mErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        //get selected movies option and also register for preference change
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mCurrentSortOrder = Integer.valueOf(
                sharedPreferences.getString(getString(R.string.pref_view_by),
                String.valueOf(Utils.MODE_POPULAR)));

        setMainScreenTitle();

        if (savedInstanceState == null) {
            getNewMoviesData();
Log.w(Utils.APP_TAG, "onCreate with null savedInstanceState");
        }
        else{
            int lastSortOrder = savedInstanceState.getInt(Utils.CURRENT_VIEW_STATE);
            if (mCurrentSortOrder==lastSortOrder) {
Log.w(Utils.APP_TAG, "onCreate with savedInstanceState restored");
                mMoviesList = savedInstanceState.getParcelableArrayList(Utils.ALL_MOVIES_DATA_OBJECT);

                showMovieGrid();
            }
            else {
                getNewMoviesData();
Log.w(Utils.APP_TAG, "onCreate with savedInstanceState but different sort option");
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(Utils.CURRENT_VIEW_STATE, mCurrentSortOrder);
        outState.putParcelableArrayList(Utils.ALL_MOVIES_DATA_OBJECT, mMoviesList);
Log.w(Utils.APP_TAG, "savedInstanceState saved");
        super.onSaveInstanceState(outState);
    }

    private void getDataFromMovieDB() {
        hideMovieGrid();
        if (Utils.isInternetConectionAvailable(this)) {
            // retrieve movies data with loader
            LoaderCallbacks<String> callback = MainActivity.this;
            Loader<String> moviesLoaderFromWeb = getSupportLoaderManager().getLoader(MOVIEDB_LOADER_ID);
            if (moviesLoaderFromWeb != null) {
Log.w(Utils.APP_TAG, "getDataFromMovieDB: restartLoader");
                getSupportLoaderManager().restartLoader(MOVIEDB_LOADER_ID, null, callback);
            }else{
Log.w(Utils.APP_TAG, "getDataFromMovieDB: initLoader");
                getSupportLoaderManager().initLoader(MOVIEDB_LOADER_ID, null, callback);
            }
        }
        else
        {
            showErrorMessage(R.string.no_internet_connection_error_msg);
        }
    }

    private void getDataFromLocalDB() {
        hideMovieGrid();
        LoaderCallbacks<String> callback = MainActivity.this;
        Loader<String> moviesLoaderFromLocalDB = getSupportLoaderManager().getLoader(LOCALDB_LOADER_ID);
        if (moviesLoaderFromLocalDB!=null){
Log.w(Utils.APP_TAG, "getDataFromLocalDB: restartLoader");
            getSupportLoaderManager().restartLoader(LOCALDB_LOADER_ID, null, callback);
        }else {
Log.w(Utils.APP_TAG, "getDataFromLocalDB: initLoader");
            getSupportLoaderManager().initLoader(LOCALDB_LOADER_ID, null, callback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /* BEGIN LoaderCallbacks Methods */
    @Override
    public Loader<String> onCreateLoader(int id, final Bundle bundle) {
        return new AsyncTaskLoader<String>(this) {

            String mMoviesData = "";

            @Override
            protected void onStartLoading() {
                if (!mMoviesData.equals("")) {
                    this.deliverResult(mMoviesData);
                } else {
                    this.forceLoad();
                }
            }

            @Override
            public String loadInBackground() {
                try {
                    if (this.getId() == LOCALDB_LOADER_ID){
                        ContentResolver resolver = getContentResolver();
                        Cursor cursor = resolver.query(
                                MoviesContract.MoviesEntry.CONTENT_URI, null,null,null,null);
                        mMoviesList = new ArrayList<>();
                        if (cursor!=null) {
                            while (cursor.moveToNext()) {
                                MovieData movieData = new MovieData(
                                        cursor.getLong(cursor.getColumnIndex(MoviesContract.MoviesEntry._ID)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_TITLE)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_RELEASE_DATE)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_POSTER)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_BACKDROP_IMAGE)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_VOTE_AVERAGE)),
                                        cursor.getString(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_SYNOPSIS)));
                                //TODO: movieData.setTrailers(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_TRAILERS));
                                //TODO: movieData.setReviews(cursor.getColumnIndex(MoviesContract.MoviesEntry.COLUMN_REVIEWS));
                                movieData.setFavorite(true);
                                mMoviesList.add(movieData);
                            }
                            cursor.close();
                        }
                        return "";
                    }
                    else {
                        URL url;
                        switch (mCurrentSortOrder) {
                            case Utils.MODE_TOP_RATED:
                                url = UrlUtils.buildUrl(UrlUtils.TOP_RATED_MOVIES_URL);
                                break;
                            default: //Utils.MODE_POPULAR
                                url = UrlUtils.buildUrl(UrlUtils.POPULAR_MOVIES_URL);
                                break;
                        }
                        return Network.getResponseFromHttpUrl(url);
                    }
                } catch (Exception e) {
                    String a="a";
                    if (a.equals("a")) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }

            @Override
            public void deliverResult(String data) {
                if (this.getId() == MOVIEDB_LOADER_ID){
                    mMoviesData = data;
                }
                super.deliverResult(data);
            }
        };
    }

    @Override
    public void onLoadFinished(Loader<String> loader, String data) {
Log.w(Utils.APP_TAG, "onLoadFinished: mProgressBar.setVisibility(View.GONE) - data null? "
        + Boolean.toString(data==null)
        + " isAbandoned:" + Boolean.toString(loader.isAbandoned())
        + " isReset:" + Boolean.toString(loader.isReset())
        + " isStarted:" + Boolean.toString(loader.isStarted())
);
        if (null != data) {
            if (loader.getId()==MOVIEDB_LOADER_ID) {
                mMoviesList = Json.getMoviesList(data);
            }
            showMovieGrid();
        } else {
            showErrorMessage(R.string.error_message_text);
        }
//
//        getLoaderManager().destroyLoader(loader.getId());
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {

    }
    /* END LoaderCallbacks Methods */

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_view_by))){
Log.w(Utils.APP_TAG, "onSharedPreferenceChanged...");
            int currentVal = Integer.valueOf(
                    sharedPreferences.getString(getString(R.string.pref_view_by),
                            String.valueOf(Utils.MODE_POPULAR)));
            if (mCurrentSortOrder != currentVal){
                //if it has changed...
Log.w(Utils.APP_TAG, "... retrieving new data");
                mCurrentSortOrder = currentVal;

                setMainScreenTitle();

                getNewMoviesData();
            }
            else{
                Log.w(Utils.APP_TAG, "... no new data retrieving :(");
            }
        }
    }

    private void setMainScreenTitle() {
        switch (mCurrentSortOrder){
            default:
            case Utils.MODE_POPULAR:
                setTitle(R.string.app_title_popular);
                break;
            case Utils.MODE_TOP_RATED:
                setTitle(R.string.app_title_top);
                break;
            case Utils.MODE_FAVORITES:
                setTitle(R.string.app_title_favorites);
                break;
        }
    }

    private void getNewMoviesData() {
        switch (mCurrentSortOrder) {
            case Utils.MODE_FAVORITES:
Log.w(Utils.APP_TAG, "calling getDataFromLocalDB()");
                getDataFromLocalDB();
                break;
            default:
Log.w(Utils.APP_TAG, "calling getDataFromMovieDB()");
                getDataFromMovieDB();
                break;
        }
    }

    private void showMovieGrid() {
Log.w(Utils.APP_TAG, "showMovieGrid: " );
        mProgressBar.setVisibility(View.GONE);
        mErrorMessage.setVisibility(View.GONE);
        mMainRecyclerView.setVisibility(View.VISIBLE);

        int numColumns = Utils.getDeviceSpanByOrientation(this);

       MoviesRecyclerViewAdapter mainMoviesAdapter = new MoviesRecyclerViewAdapter(this, mMoviesList,
                new MoviesRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MovieData movieData) {
                //prepare the intent to call detail activity
                Intent movieDetailIntent = new Intent(getApplicationContext(), MovieDetails.class);
                //And send it to the detail activity
                movieDetailIntent.putExtra(Utils.SINGLE_MOVIE_DETAILS_OBJECT, movieData);
                startActivityForResult(movieDetailIntent, ID_FOR_ACTIVITY_RESULT);
            }
        });

        mMainRecyclerView.setLayoutManager(new GridLayoutManager(
                this,
                numColumns,
                OrientationHelper.VERTICAL,
                false));

        mMainRecyclerView.setAdapter(mainMoviesAdapter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == ID_FOR_ACTIVITY_RESULT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK && mCurrentSortOrder == Utils.MODE_FAVORITES) {
                //check if movie was removed from favorites and remove it also from the main screen
                MovieData returnedMovieData = intent.getParcelableExtra(Utils.SINGLE_MOVIE_DETAILS_OBJECT);
                if (!returnedMovieData.isFavorite()) {
                    for (MovieData movieData : mMoviesList) {
                        if (movieData.getId() == returnedMovieData.getId()) {
                            int itemToRemove = mMoviesList.indexOf(movieData);
                            mMoviesList.remove(itemToRemove);
                            //MoviesRecyclerViewAdapter mMoviesAdapter = (MoviesRecyclerViewAdapter)mMainRecyclerView.getAdapter();
                            //mMoviesAdapter.notifyItemRemoved(itemToRemove);
                            //mMoviesAdapter.notifyItemRangeChanged(itemToRemove, mMoviesList.size());
                            break;
                        }
                    }
                    showMovieGrid();
                }
            }
        }
    }

    private void hideMovieGrid() {
Log.w(Utils.APP_TAG, "hideMovieGrid: ");
        mProgressBar.setVisibility(View.VISIBLE);
        mErrorMessage.setVisibility(View.GONE);
        mMainRecyclerView.setVisibility(View.GONE);
    }

    private void showErrorMessage(int res_error_message) {
Log.w(Utils.APP_TAG, "showErrorMessage: " );
        /* First, hide the currently visible data */
        mProgressBar.setVisibility(View.GONE);
        mMainRecyclerView.setVisibility(View.GONE);
        mErrorMessage.setText(res_error_message);
        mErrorMessage.setVisibility(View.VISIBLE);
    }

    /* START Menu Methods */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, Settings.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    /* END Menu Methods **/
}
