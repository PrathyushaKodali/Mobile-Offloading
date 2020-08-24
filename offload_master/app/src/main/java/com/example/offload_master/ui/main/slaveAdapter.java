package com.example.offload_master.ui.main;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.offload_master.R;

import org.json.JSONException;
import org.json.JSONObject;

public class slaveAdapter extends BaseAdapter {

    private final Context context;
    private final String[] slaves;

    public slaveAdapter(Context context, String[] slaves) {
        this.context = context;
        this.slaves = slaves;
    }

    @Override
    public int getCount() {
        return slaves.length;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        JSONObject oldObj;
        double battery=0;
        String name="";
        try {
            oldObj = new JSONObject(slaves[i]);
            name = oldObj.getString("name");
            battery= oldObj.getDouble("battery");

        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (view == null) {
            final LayoutInflater layoutInflater = LayoutInflater.from(context);
            view = layoutInflater.inflate(R.layout.slave_info, null);
        }

        final ProgressBar progressBar = (ProgressBar)view.findViewById(R.id.progressBar6);
        progressBar.setProgress((int)battery);
        final TextView nameTextView = (TextView)view.findViewById(R.id.slaveName);
        nameTextView.setText(name);
        final TextView batteryLevel = (TextView)view.findViewById(R.id.batteryLevel);
        batteryLevel.setText(battery+"%");

        return view;
    }
}
