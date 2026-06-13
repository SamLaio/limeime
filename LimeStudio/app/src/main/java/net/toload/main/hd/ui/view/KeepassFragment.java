package net.toload.main.hd.ui.view;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import net.toload.main.hd.R;

public class KeepassFragment extends Fragment {

    public static KeepassFragment newInstance() {
        return new KeepassFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_keepass, container, false);
        NestedScrollView scrollView = root.findViewById(R.id.keepass_scroll);
        if (scrollView != null) {
            ScrollableTabHelper.applyToNestedScrollView(getActivity(), scrollView);
        }
        return root;
    }
}
