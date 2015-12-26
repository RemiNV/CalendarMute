package com.PKH.calendarmute.activites;

import com.PKH.calendarmute.PreferencesManager;
import com.PKH.calendarmute.R;
import com.PKH.calendarmute.calendar.CalendarProvider;
import com.PKH.calendarmute.models.Calendar;
import com.PKH.calendarmute.service.MuteService;
import com.PKH.calendarmute.views.CalendarAdapter;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

public class CalendarsFragment extends Fragment {
	
	private ListView lstAgendas;

    private static final int CALENDAR_PERMISSION_REQUEST = 1;
    private static final int CALENDAR_PERMISSION_REQUEST_FORCE_REFRESH = 2;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		
		this.setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View res = inflater.inflate(R.layout.layout_lst_agendas, container, false);
		
		lstAgendas = (ListView) res.findViewById(R.id.lst_calendars);
		
		// Fill calendars
		refreshCalendars(false);
		
		return res;
	}
	
	public void refreshCalendars(boolean forceRefresh) {

        Activity activity = getActivity();
        if(activity == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            // Not showing explanations, this should be very obvious
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
        }
        else {
            refreshCalendarsWithPermission(forceRefresh);
        }
	}

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode) {
            case CALENDAR_PERMISSION_REQUEST:
            case CALENDAR_PERMISSION_REQUEST_FORCE_REFRESH:

                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    refreshCalendarsWithPermission(requestCode == CALENDAR_PERMISSION_REQUEST_FORCE_REFRESH);
                }
                else {
                    // The user wants to use CalendarMute without calendar (not really a genius)
                    Activity activity = getActivity();
                    if(activity == null) {
                        return;
                    }

                    Toast.makeText(activity, R.string.calendar_permission_denied,
                            Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    /**
     * Method used to refresh calendars once we have made sure that we have the necessary permissions
     * @param forceRefresh
     */
    private void refreshCalendarsWithPermission(boolean forceRefresh) {
        Calendar[] savedCalendars;
        if (!forceRefresh && (savedCalendars = CalendarProvider.getCachedCalendars()) != null) {
            fillCalendars(savedCalendars);
        } else {
            new CalendarGetter().execute(true);
        }
    }
	
	private class CalendarGetter extends AsyncTask<Boolean, Void, Calendar[]> {
		@Override
		protected Calendar[] doInBackground(Boolean... params) {
			// Fetch calendars
			Activity a = getActivity();
			if(a == null) // Fragment already detached
				return null;
			
			CalendarProvider provider = new CalendarProvider(a);

			return provider.listCalendars(params[0]);
		}
		
		@Override
		protected void onPostExecute(Calendar[] result) {
			fillCalendars(result);
		}
	}
	
	private void fillCalendars(Calendar[] calendars) {
		
		Activity a = getActivity();
		if(a == null) // Fragment already detached
			return;
		
		if(calendars == null) {
			Toast.makeText(a, R.string.calendar_listing_error, Toast.LENGTH_LONG).show();
			return;
		}
		
		CalendarAdapter adapter = new CalendarAdapter(getActivity(), calendars);
		lstAgendas.setAdapter(adapter);
		
		// Restore checked items in the list
		for(int i=0, max = lstAgendas.getCount(); i<max; i++) {
			lstAgendas.setItemChecked(i, adapter.getItem(i).isChecked());
		}
		
		adapter.setItemCheckedChangedListener(new CalendarAdapter.ItemCheckedChangedListener() {
			@Override
			public void onItemCheckedChanged() {
				
				Activity a = getActivity();
				PreferencesManager.saveCalendars(a, lstAgendas.getCheckedItemIds());
				
				// Remove cached calendars (now invalid)
				CalendarProvider.invalidateCalendars();
				
				// Launch service to check if there are events now
				MuteService.startIfNecessary(a);
			}
		});
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
		case R.id.menu_refresh_calendars:
			refreshCalendars(true);
			return true;
		default:
			return false;
		}
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.main, menu);
	}
}
