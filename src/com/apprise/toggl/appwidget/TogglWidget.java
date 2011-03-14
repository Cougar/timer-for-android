package com.apprise.toggl.appwidget;

import static com.apprise.toggl.Toggl.TOGGL_PREFS;
import static com.apprise.toggl.Toggl.PREF_API_TOKEN;

import java.util.Date;

import com.apprise.toggl.AccountActivity;
import com.apprise.toggl.R;
import com.apprise.toggl.TaskActivity;
import com.apprise.toggl.TasksActivity;
import com.apprise.toggl.Util;
import com.apprise.toggl.remote.SyncService;
import com.apprise.toggl.storage.DatabaseAdapter;
import com.apprise.toggl.storage.DatabaseAdapter.Tasks;
import com.apprise.toggl.storage.models.Task;
import com.apprise.toggl.storage.models.User;
import com.apprise.toggl.tracking.TimeTrackingService;
import com.ocpsoft.pretty.time.PrettyTime;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.view.View;
import android.widget.RemoteViews;
import android.os.IBinder;
import android.util.Log;

public class TogglWidget extends AppWidgetProvider {
  
	public static final String TAG = "TogglWidget";
	
	public static final String ACTION_TOGGLE_TRACKING = "com.apprise.toggl.appwidget.ACTION_TOGGLE_TRACKING";
	public static final String ACTION_PREVIOUS = "com.apprise.toggl.appwidget.ACTION_PREVIOUS";
	public static final String ACTION_NEXT = "com.apprise.toggl.appwidget.ACTION_NEXT";
	
	public static final String ACTION_INIT = "com.apprise.toggl.appwidget.ACTION_INIT";
	public static final String ACTION_USER_CHANGED = "com.apprise.toggl.appwidget.ACTION_USER_CHANGED";
	public static final String ACTION_RESET = "com.apprise.toggl.appwidget.ACTION_RESET";
	public static final String ACTION_UPDATE_VIEWS = "com.apprise.toggl.appwidget.ACTION_UPDATE_VIEWS";
	public static final String ACTION_STOPPED_TRACKING = "com.apprise.toggl.appwidget.ACTION_STOPPED_TRACKING";
	
	public static final String START_TRACKING_EXTRA = "com.apprise.toggl.appwidget.START_TRACKING_EXTRA";
	public static final String STOP_TRACKING_EXTRA = "com.apprise.toggl.appwidget.STOP_TRACKING_EXTRA";
	public static final String TASK_ID_EXTRA = "com.apprise.toggl.appwidget.TASK_ID_EXTRA";
	public static final String USER_ID_EXTRA = "com.apprise.toggl.appwidget.USER_ID_EXTRA";
	
	private static SharedPreferences settings;
	private static DatabaseAdapter dbAdapter;
	private static User currentUser;
	private static Cursor tasksCursor;
	private static boolean isTracking = false;
	
  @Override 
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) { 
  	updateWidgetViaService(context, ACTION_INIT);
  }
  
  @Override
  public void onDeleted(Context context, int[] appWidgetIds) {
    for (int widgetId : appWidgetIds) {
    	
    } 
  }
  
  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    
    Log.d(TAG, "Received intent: " + intent.getAction());

    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TogglWidget.class));
    final int N = appWidgetIds.length;
    
    if (intent.getAction().equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
    	
    	isTracking = false;
    	
    } else if (intent.getAction().equals(ACTION_PREVIOUS) && !isTracking) {
  		Log.d(TAG, "Previous button clicked.");
    	
  		if (needsReInit()) {
  			updateWidgetViaService(context, ACTION_INIT);
  			return;
  		}

      for (int i = 0; i < N; i++) { 
        int appWidgetId = appWidgetIds[i]; 

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);

        previousTask();

        updateViews(context, views);

        appWidgetManager.updateAppWidget(appWidgetId, views);
      }
    }
    else if (intent.getAction().equals(ACTION_NEXT)  && !isTracking) {
  		Log.d(TAG, "Next button clicked.");
  		
  		if (needsReInit()) {
  			updateWidgetViaService(context, ACTION_INIT);
  			return;
  		}
  		
      for (int i = 0; i < N; i++) { 
        int appWidgetId = appWidgetIds[i]; 

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);

        nextTask();

        updateViews(context, views);

        appWidgetManager.updateAppWidget(appWidgetId, views);
      }
    }
    else if (intent.getAction().equals(ACTION_TOGGLE_TRACKING)) {
  		Log.d(TAG, "Toggle button clicked clicked.");
    	
  		if (needsReInit()) {
  			updateWidgetViaService(context, ACTION_INIT);
  			return;
  		}
  		
      for (int i = 0; i < N; i++) { 
        int appWidgetId = appWidgetIds[i]; 

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);

        Intent trackingIntent = new Intent(context, TimeTrackingService.class);
        trackingIntent.putExtra(USER_ID_EXTRA, currentUser._id);
        
      	int position = tasksCursor.getPosition();
      	tasksCursor.requery();
      	tasksCursor.moveToPosition(position);
      	
      	long currentTaskId = tasksCursor.getLong(tasksCursor.getColumnIndex(Tasks._ID));
        
        if (isTracking) {
        	trackingIntent.putExtra(TASK_ID_EXTRA, currentTaskId);
        	
        	trackingIntent.putExtra(STOP_TRACKING_EXTRA, true);
        	
        	context.startService(trackingIntent);
        	
        	views.setImageViewResource(R.id.widget_toggle_tracking, R.drawable.widget_middle_play_selector);
        	
        	isTracking = false;
          
          Intent tasksIntent = new Intent(context, TasksActivity.class);
          PendingIntent pendingTasks = PendingIntent.getActivity(context, 0, tasksIntent, PendingIntent.FLAG_UPDATE_CURRENT);          
          
          views.setOnClickPendingIntent(R.id.widget_top_layout, pendingTasks);
          
          Intent addIntent = new Intent(context, TasksActivity.class);
          PendingIntent pendingAdd = PendingIntent.getActivity(context, 0, addIntent, PendingIntent.FLAG_UPDATE_CURRENT);

          views.setOnClickPendingIntent(R.id.widget_add, pendingAdd);
        }
        else {
        	Task startTrackingTask = dbAdapter.findTask(currentTaskId);
        	
        	Date startDate = Util.parseStringToDate(startTrackingTask.start);
        	
        	if (!Util.todaysDateOrLater(startDate)) {
            Task continueTask = dbAdapter.createDirtyTask();
            continueTask.updateAttributes(startTrackingTask);

            String now = Util.formatDateToString(Util.currentDate());
            
            continueTask.description = startTrackingTask.description;    
            continueTask.start = now;
            continueTask.stop = now;
            continueTask.duration = 0;
            continueTask.id = 0;
            dbAdapter.updateTask(continueTask);
            
            trackingIntent.putExtra(TASK_ID_EXTRA, continueTask._id);
        	}
        	else {
        		trackingIntent.putExtra(TASK_ID_EXTRA, currentTaskId);
        	}
        	
        	trackingIntent.putExtra(START_TRACKING_EXTRA, true);
        	
        	context.startService(trackingIntent);
        	
        	views.setImageViewResource(R.id.widget_toggle_tracking, R.drawable.widget_middle_pause_selector);
        	
        	isTracking = true;
        }
        
        tasksCursor.deactivate();
        
        appWidgetManager.updateAppWidget(appWidgetId, views);
      }
    }
    else if (intent.getAction().equals(TimeTrackingService.BROADCAST_SECOND_ELAPSED)) {
  		Log.d(TAG, "Second elapsed broadcast.");  		
  		
  		if (currentUser != null && tasksCursor != null) {
  		
	  		isTracking = true;
	  		
	  		if (SyncService.isBusySyncing) {
	  		  // wait for sync to be completed, so cursor is up-to-date
	  		  return;
	  		}
	    	
	      for (int i = 0; i < N; i++) { 
	        int appWidgetId = appWidgetIds[i]; 
	
	        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
	
	        // --
	        
	        // make sure correct task is displayed
	        long trackedTaskId = intent.getLongExtra(TimeTrackingService.TRACKED_TASK_ID, -1);
	        
	      	int position = tasksCursor.getPosition();
	      	tasksCursor.requery();
	      	tasksCursor.moveToPosition(position);
	      	
	      	long displayedTaskId = tasksCursor.getLong(tasksCursor.getColumnIndex(Tasks._ID));
	      	
	      	if (displayedTaskId != trackedTaskId) {
	      		tasksCursor.moveToPosition(-1);
	      		
	      		while (tasksCursor.moveToNext()) {
	      			long taskId = tasksCursor.getLong(tasksCursor.getColumnIndex(Tasks._ID));
	      			
	      			if (taskId == trackedTaskId) {
	      				break;
	      			}
	      		}
	      	}
	      	
	      	updateViews(context, views);
	      	
	      	tasksCursor.deactivate(); 
	      	
	      	// --
	      	
	        long duration = intent.getLongExtra(TimeTrackingService.TRACKED_TASK_DURATION, 0);
	        
	        views.setTextViewText(R.id.widget_top_right_text, Util.secondsToHMS(duration));
	
	        views.setImageViewResource(R.id.widget_toggle_tracking, R.drawable.widget_middle_pause_selector);
	        
	        appWidgetManager.updateAppWidget(appWidgetId, views);
	      }

  		}
    }
  }
  
  private void nextTask() {
  	moveCursor(tasksCursor.getPosition() + 1);
  }
  
  private void previousTask() {
  	moveCursor(tasksCursor.getPosition() - 1);
  }

  private void moveCursor(int position) {
  	tasksCursor.requery();
  	int count = tasksCursor.getCount();
  	
  	if (position < 0) {
  		// clicked previous when on first
  		position = count - 1;
  	} else if (position > count - 1) {
  		// clicked next when on last
  		position = 0;
  	}
  	
  	tasksCursor.moveToPosition(position);
  }
  
  private boolean needsReInit() {
  	boolean reInit = tasksCursor == null;
  	
  	if (reInit) {
  		Log.d(TAG, "re-initiating widget (cursor has been garbage collected)");
  	}
  	
  	return reInit;
  }
  
  private static void updateViews(Context context, RemoteViews views) {
    
    if (!tasksCursor.isClosed() && tasksCursor.getCount() > 0) {
    
    	Task task = dbAdapter.findTask(tasksCursor.getLong(tasksCursor.getColumnIndex(Tasks._ID)));
    	
  		views.setTextViewText(R.id.widget_top_text, task.description);
  		
  		StringBuilder s = new StringBuilder();
  		
  		if (task.project != null) {
  			if (task.project.client != null) {
  				s.append(task.project.client.name);
  				s.append(" - ");
  			}
  			
  			s.append(task.project.name);
  		}
  		
  		views.setTextViewText(R.id.widget_bottom_text, s.toString());
  
  		Date startDate = Util.parseStringToDate(task.start);
  		
  		if (Util.todaysDateOrLater(startDate)) {
  		  views.setTextViewText(R.id.widget_top_right_text, Util.secondsToHMS(task.duration));
  		}
  		else {
  	    PrettyTime pretty = new PrettyTime();
  		  views.setTextViewText(R.id.widget_top_right_text, pretty.format(startDate));  
  		}
  		
  		tasksCursor.deactivate();
    }
  }
  
  private void updateWidgetViaService(Context context, String ...actions) {
    Intent intent = new Intent(context, TogglWidgetService.class);
    
    for (int i = 0; i < actions.length; i++) {
    	intent.putExtra(actions[i], true);
    }
    
    context.startService(intent);
  }
  
  public static class TogglWidgetService extends Service {

  	@Override
  	public void onStart(Intent intent, int startId) {
  		super.onStart(intent, startId);
  		
  		if (intent.getBooleanExtra(ACTION_INIT, false)) {
  			initWidget();
  		}
  		else if(intent.getBooleanExtra(ACTION_UPDATE_VIEWS, false)) {
  			
  			if (tasksCursor != null) {
  				int position = tasksCursor.getPosition();
  				
  				tasksCursor.requery();
  				tasksCursor.moveToPosition(position);

	        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
	        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, TogglWidget.class));
	        final int N = appWidgetIds.length;
	        
	  	    for (int i = 0; i < N; i++) { 
	  	      int appWidgetId = appWidgetIds[i]; 
	
	  	      RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget); 

	  	      updateViews(this, views);
	  	      
	  	      appWidgetManager.updateAppWidget(appWidgetId, views);
	  	    }
  			}
  		}
  		else if(intent.getBooleanExtra(ACTION_STOPPED_TRACKING, false)) {
  			isTracking = false;
  			
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, TogglWidget.class));
        final int N = appWidgetIds.length;
  	    
  	    for (int i = 0; i < N; i++) { 
  	      int appWidgetId = appWidgetIds[i]; 

  	      RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget); 

  	      views.setImageViewResource(R.id.widget_toggle_tracking, R.drawable.widget_middle_play_selector);
  	      
  	      appWidgetManager.updateAppWidget(appWidgetId, views);
  	    }
  		}
  		else if(intent.getBooleanExtra(ACTION_USER_CHANGED, false) || intent.getBooleanExtra(ACTION_RESET, false)) {
  			if (tasksCursor != null) {
  				tasksCursor.close();	
  			}
  			if (dbAdapter != null) {
  				dbAdapter.close();	
  			}

  	  	currentUser = null;
  	  	
  	  	initWidget();
  	  }
  	}
  	
		@Override
		public IBinder onBind(Intent arg0) {
			// widget cannot bind a service
			return null;
		}
		
		public void initWidget() {
	    settings = this.getSharedPreferences(TOGGL_PREFS, MODE_PRIVATE);
	  	dbAdapter = new DatabaseAdapter(this, null);
	  	dbAdapter.open();
	    
	    String apiToken = getAPIToken();

	    if (apiToken != null) {
	    	currentUser = dbAdapter.findUserByApiToken(apiToken);	

	    	// if token exists and user exists, then widget is functional
	      if (currentUser != null) {
	      	dbAdapter.setCurrentUser(currentUser);
	      }
	    }
	    
      AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
      int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, TogglWidget.class));
      final int N = appWidgetIds.length;
	    
	    for (int i = 0; i < N; i++) { 
	      int appWidgetId = appWidgetIds[i]; 

	      RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget); 
	      
	      // if no user signed in, cannot use widget
	      if (currentUser != null) {
	      	tasksCursor = dbAdapter.findAllTasks(false);
	      	
	      	if (tasksCursor.getCount() == 0) {
	      		views.setTextViewText(R.id.widget_top_text, this.getString(R.string.widget_no_tasks));
	      		
	      		tasksCursor.close();
	      	}
	      	else {
	      		tasksCursor.moveToFirst();
	      		
	      		views.setViewVisibility(R.id.widget_bottom_layout, View.VISIBLE);
	      		
	      		updateViews(this, views);
	      		
	          Intent previousIntent = new Intent(ACTION_PREVIOUS);
	          PendingIntent pendingPrevious = PendingIntent.getBroadcast(this, 0, previousIntent, 0);
	        	
	        	views.setOnClickPendingIntent(R.id.widget_previous, pendingPrevious);
	        	
	          Intent nextIntent = new Intent(ACTION_NEXT);
	          PendingIntent pendingNext = PendingIntent.getBroadcast(this, 0, nextIntent, 0);
	          
	        	views.setOnClickPendingIntent(R.id.widget_next, pendingNext);
	        	
	          Intent toggleIntent = new Intent(ACTION_TOGGLE_TRACKING);
	          PendingIntent pendingToggle = PendingIntent.getBroadcast(this, 0, toggleIntent, 0);
	        	
	        	views.setOnClickPendingIntent(R.id.widget_toggle_tracking, pendingToggle);
	      	}

          Intent addIntent = new Intent(this, TasksActivity.class);
          PendingIntent pendingAdd = PendingIntent.getActivity(this, 0, addIntent, 0);

          views.setOnClickPendingIntent(R.id.widget_add, pendingAdd);
	      	
	        Intent tasksIntent = new Intent(this, TasksActivity.class);
	        PendingIntent pendingTasks = PendingIntent.getActivity(this, 0, tasksIntent, PendingIntent.FLAG_CANCEL_CURRENT);
	        
	        views.setOnClickPendingIntent(R.id.widget_top_layout, pendingTasks);
	      }
	      else {
	      	views.setTextViewText(R.id.widget_top_text, this.getString(R.string.widget_not_signed_in));
	      	views.setTextViewText(R.id.widget_bottom_text, "");
	      	views.setTextViewText(R.id.widget_top_right_text, "");
	      	
	      	views.setViewVisibility(R.id.widget_bottom_layout, View.GONE);
	      	
	        Intent taskIntent = new Intent(this, AccountActivity.class);
	        PendingIntent pendingTask = PendingIntent.getActivity(this, 0, taskIntent, PendingIntent.FLAG_CANCEL_CURRENT);
	        
	      	views.setOnClickPendingIntent(R.id.widget_top_layout, pendingTask);
	      }
	      
	      views.setImageViewResource(R.id.widget_toggle_tracking, R.drawable.widget_middle_play_selector);
	      
	      appWidgetManager.updateAppWidget(appWidgetId, views);
	    }
		}
		
	  private String getAPIToken() {
	    return settings.getString(PREF_API_TOKEN, null);
	  }
  	
  }

}
