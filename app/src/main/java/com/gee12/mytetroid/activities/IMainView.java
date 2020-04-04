package com.gee12.mytetroid.activities;

import android.os.Parcelable;

import com.gee12.mytetroid.model.ITetroidObject;
import com.gee12.mytetroid.model.TetroidFile;
import com.gee12.mytetroid.model.TetroidRecord;

public interface IMainView extends Parcelable {

    void onMainPageCreated();
    void openFolder(String pathUri);
    void openAttach(TetroidFile file);
    void updateMainToolbar(int viewId, String title);
    void openFoundObject(ITetroidObject found);
    void openMainPage();
    void closeFoundFragment();
    void openRecord(TetroidRecord record);
    void updateTags();
    void updateNodes();
    void chooseFile();
}
