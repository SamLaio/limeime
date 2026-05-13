package net.toload.main.hd.ui.view;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.toload.main.hd.R;
import net.toload.main.hd.SearchServer;
import net.toload.main.hd.ui.LIMEPreference;
import net.toload.main.hd.ui.LIMESettings;

public class LimePreferenceFragment extends Fragment {

    public static LimePreferenceFragment newInstance() {
        return new LimePreferenceFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lime_preference_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.lime_preference_host, new LIMEPreference.PrefsFragment())
                    .commit();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Mirror LIMEPreference.onPause() — flush the cache when leaving prefs
        Activity act = getActivity();
        if (act instanceof LIMESettings) {
            net.toload.main.hd.ui.controller.ManageImController ctrl =
                    ((LIMESettings) act).getManageImController();
            if (ctrl != null) {
                SearchServer ss = ctrl.getSearchServer();
                if (ss != null) ss.initialCache();
            }
        }
    }
}
