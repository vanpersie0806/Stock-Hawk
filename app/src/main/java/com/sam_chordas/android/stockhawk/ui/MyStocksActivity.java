package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteHistoryColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QueryEvent;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.service.StockIntentService;
import com.sam_chordas.android.stockhawk.service.StockTaskService;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;

public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>{

  /**
   * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
   */

  /**
   * Used to store the last screen title. For use in {@link #restoreActionBar()}.
   */
  private CharSequence mTitle;
  private Intent mServiceIntent;
  private ItemTouchHelper mItemTouchHelper;
  private static final int CURSOR_LOADER_ID = 0;
  private QuoteCursorAdapter mCursorAdapter;
  private Context mContext;
  private Cursor mCursor;
  boolean isConnected;
  private static final String LINE_GRAPH_FRAGMENT_TAG = "LGFT";
  private boolean mTwoPane;

  private ArrayList<String> dateList = new ArrayList<String>();
  private ArrayList<String> adjCloseList = new ArrayList<String>();
  private ArrayList<String> openList = new ArrayList<String>();
  private ArrayList<String> closeList = new ArrayList<String>();
  private ArrayList<String> highList = new ArrayList<String>();
  private ArrayList<String> lowList = new ArrayList<String>();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mContext = this;

    if(findViewById(R.id.line_graph_container) != null){
      mTwoPane = true;
      if(savedInstanceState == null){

        getFragmentManager().beginTransaction()
                .add(R.id.line_graph_container, new LineGraphActivityFragment(), LINE_GRAPH_FRAGMENT_TAG)
                .commit();
      }
    } else{
      mTwoPane = false;
    }

    ConnectivityManager cm =
        (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    isConnected = activeNetwork != null &&
        activeNetwork.isConnectedOrConnecting();
    setContentView(R.layout.activity_my_stocks);
    // The intent service is for executing immediate pulls from the Yahoo API
    // GCMTaskService can only schedule tasks, they cannot execute immediately

    mServiceIntent = new Intent(this, StockIntentService.class);
    if (savedInstanceState == null){
      // Run the initialize task service so that some stocks appear upon an empty database
      mServiceIntent.putExtra("tag", "init");
      if (isConnected){
        //emptyView.setVisibility(View.INVISIBLE);
        startService(mServiceIntent);
      } else{
        networkToast();
      }
    }

    RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    recyclerView.setLayoutManager(new LinearLayoutManager(this));
    getLoaderManager().initLoader(CURSOR_LOADER_ID, null, this);

    mCursorAdapter = new QuoteCursorAdapter(this, null);
    recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
            new RecyclerViewItemClickListener.OnItemClickListener() {
              @Override public void onItemClick(View v, int position) {
                mCursor.moveToPosition(position);
                int symbolIndex = mCursor.getColumnIndex("symbol");
                String sendSymbol = mCursor.getString(symbolIndex);

                String selection = QuoteHistoryColumns.SYMBOL + " = ? AND " + QuoteHistoryColumns.ISCURRENT + " = ? ";
                String sortOrder = QuoteHistoryColumns.DATE + " ASC ";
                Cursor mQueryCursor = getContentResolver().query(QuoteProvider.QuotesHistory.CONTENT_URI,
                        null,
                        selection,
                        new String[] {sendSymbol,"1"},
                        sortOrder);
                getData(mQueryCursor);
                mQueryCursor.close();
                if(mTwoPane){
                  Bundle args = new Bundle();
                  args.putString(LineGraphActivityFragment.SYMBOL_FOR_LINE_GRAPH,sendSymbol);
                  args.putStringArrayList(LineGraphActivityFragment.DATE_LIST,dateList);
                  args.putStringArrayList(LineGraphActivityFragment.ADJ_CLOSE_LIST,adjCloseList);
                  args.putStringArrayList(LineGraphActivityFragment.CLOSE_LIST,closeList);
                  args.putStringArrayList(LineGraphActivityFragment.HIGH_LIST,highList);
                  args.putStringArrayList(LineGraphActivityFragment.LOW_LIST,lowList);
                  args.putStringArrayList(LineGraphActivityFragment.OPEN_LIST,openList);

                  LineGraphActivityFragment fragment = new LineGraphActivityFragment();
                  fragment.setArguments(args);
                  getFragmentManager().beginTransaction()
                          .replace(R.id.line_graph_container, fragment, LINE_GRAPH_FRAGMENT_TAG)
                          .commit();
                }else {
                  Intent intent = new Intent(mContext, LineGraphActivity.class);
                  Bundle args = new Bundle();
                  args.putString(LineGraphActivityFragment.SYMBOL_FOR_LINE_GRAPH, sendSymbol);
                  args.putStringArrayList(LineGraphActivityFragment.DATE_LIST,dateList);
                  args.putStringArrayList(LineGraphActivityFragment.ADJ_CLOSE_LIST,adjCloseList);
                  args.putStringArrayList(LineGraphActivityFragment.CLOSE_LIST,closeList);
                  args.putStringArrayList(LineGraphActivityFragment.HIGH_LIST,highList);
                  args.putStringArrayList(LineGraphActivityFragment.LOW_LIST,lowList);
                  args.putStringArrayList(LineGraphActivityFragment.OPEN_LIST,openList);
                  intent.putExtras(args);
                  startActivity(intent);
                }
              }
            }));
    recyclerView.setAdapter(mCursorAdapter);

    // handle Views when there is no connection
//    if (isConnected) {
//      recyclerView.setVisibility(View.VISIBLE);
//      emptyView.setVisibility(View.GONE);
//    } else {
//      recyclerView.setVisibility(View.GONE);
//      emptyView.setVisibility(View.VISIBLE);
//    }

    FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
    fab.attachToRecyclerView(recyclerView);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        if (isConnected){
          new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
              .content(R.string.content_test)
              .inputType(InputType.TYPE_CLASS_TEXT)
              .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                @Override public void onInput(MaterialDialog dialog, CharSequence input) {
                  // On FAB click, receive user input. Make sure the stock doesn't already exist
                  // in the DB and proceed accordingly
                  Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                      new String[] { QuoteColumns.SYMBOL }, QuoteColumns.SYMBOL + "= ?",
                      new String[] { input.toString() }, null);
                  if (c.getCount() != 0) {
                    Toast toast =
                        Toast.makeText(MyStocksActivity.this, "This stock is already saved!",
                            Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
                    toast.show();
                    return;
                  } else {
                    // Add the stock to DB
                    mServiceIntent.putExtra("tag", "add");
                    mServiceIntent.putExtra("symbol", input.toString());
                    startService(mServiceIntent);
                  }
                }
              })
              .show();
        } else {
          networkToast();
        }

      }
    });

    ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
    mItemTouchHelper = new ItemTouchHelper(callback);
    mItemTouchHelper.attachToRecyclerView(recyclerView);

    mTitle = getTitle();
    if (isConnected){
      long period = 3600L;
      long flex = 10L;
      String periodicTag = "periodic";

      // create a periodic task to pull stocks once every hour after the app has been opened. This
      // is so Widget data stays up to date.
      PeriodicTask periodicTask = new PeriodicTask.Builder()
          .setService(StockTaskService.class)
          .setPeriod(period)
          .setFlex(flex)
          .setTag(periodicTag)
          .setRequiredNetwork(Task.NETWORK_STATE_CONNECTED)
          .setRequiresCharging(false)
          .build();
      // Schedule task with tag "periodic." This ensure that only the stocks present in the DB
      // are updated.
      GcmNetworkManager.getInstance(this).schedule(periodicTask);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    EventBus.getDefault().register(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(QueryEvent event){
    Toast.makeText(this, event.mMessage, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onResume() {
    super.onResume();
    getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, this);
  }

  public void networkToast(){
    Toast.makeText(mContext, getString(R.string.network_toast), Toast.LENGTH_SHORT).show();
  }

  public void restoreActionBar() {
    ActionBar actionBar = getSupportActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
    actionBar.setDisplayShowTitleEnabled(true);
    actionBar.setTitle(mTitle);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      getMenuInflater().inflate(R.menu.my_stocks, menu);
      restoreActionBar();
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    if (id == R.id.action_change_units){
      // this is for changing stock changes from percent value to dollar value
      Utils.showPercent = !Utils.showPercent;
      this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args){
    // This narrows the return to only the stocks that are most current.
    return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
        new String[]{ QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
        QuoteColumns.ISCURRENT + " = ?",
        new String[]{"1"},
        null);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data){
    mCursorAdapter.swapCursor(data);
    mCursor = data;
    //notify detail widget
    Intent dataUpdatedIntent = new Intent(StockTaskService.ACTION_DATA_UPDATED).setPackage(getPackageName());
    mContext.sendBroadcast(dataUpdatedIntent);
    
    TextView emptyView = (TextView)findViewById(R.id.empty_view);
    if(data.getCount() == 0){
      emptyView.setVisibility(View.VISIBLE);
    }else{
      emptyView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader){
    mCursorAdapter.swapCursor(null);
  }

  public void getData(Cursor cursor){

    int dateIndex = cursor.getColumnIndex(QuoteHistoryColumns.DATE);
    int adjCloseIndex = cursor.getColumnIndex(QuoteHistoryColumns.ADJ_CLOSE);
    int openIndex = cursor.getColumnIndex(QuoteHistoryColumns.OPEN);
    int closeIndex = cursor.getColumnIndex(QuoteHistoryColumns.CLOSE);
    int highIndex = cursor.getColumnIndex(QuoteHistoryColumns.HIGH);
    int lowIndex = cursor.getColumnIndex(QuoteHistoryColumns.LOW);

    dateList.clear();
    adjCloseList.clear();
    openList.clear();
    closeList.clear();
    highList.clear();
    lowList.clear();

    if(dateIndex != -1 && cursor.getCount() != 0 && adjCloseIndex != -1){
      while(cursor.moveToNext()){
        dateList.add(cursor.getString(dateIndex));
        adjCloseList.add(cursor.getString(adjCloseIndex));
        openList.add(cursor.getString(openIndex));
        closeList.add(cursor.getString(closeIndex));
        highList.add(cursor.getString(highIndex));
        lowList.add(cursor.getString(lowIndex));

      }
    }

  }


}