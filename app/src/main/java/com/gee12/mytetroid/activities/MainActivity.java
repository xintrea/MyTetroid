package com.gee12.mytetroid.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBarDrawerToggle;

import android.view.ContextMenu;
import android.view.Gravity;
import android.view.View;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.ViewFlipper;

import com.gee12.mytetroid.LogManager;
import com.gee12.mytetroid.R;
import com.gee12.mytetroid.SettingsManager;
import com.gee12.mytetroid.Utils;
import com.gee12.mytetroid.crypt.CryptManager;
import com.gee12.mytetroid.data.DataManager;
import com.gee12.mytetroid.data.TetroidFile;
import com.gee12.mytetroid.data.TetroidNode;
import com.gee12.mytetroid.data.TetroidRecord;
import com.gee12.mytetroid.views.FilesListAdapter;
import com.gee12.mytetroid.views.NodesListAdapter;
import com.gee12.mytetroid.views.ActivityDialogs;
import com.gee12.mytetroid.views.RecordsListAdapter;

//import net.rdrei.android.dirchooser.DirectoryChooserActivity;
//import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import net.cachapa.expandablelayout.ExpandableLayout;

import java.io.File;

import lib.folderpicker.FolderPicker;
import pl.openrnd.multilevellistview.ItemInfo;
import pl.openrnd.multilevellistview.MultiLevelListView;
import pl.openrnd.multilevellistview.OnItemClickListener;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    public static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    public static final int REQUEST_CODE_PERMISSION_REQUEST = 2;
    public static final int REQUEST_CODE_SETTINGS_ACTIVITY = 3;

    public static final int FILE_BROWSE = 1;
    public static final int GET_CONTENT = 2;
    public static final int OPEN_DOC = 3;

    public static final int OPEN_RECORD_MENU_ITEM_ID = 1;
    public static final int SHOW_FILES_MENU_ITEM_ID = 2;
    public static final int OPEN_RECORD_FOLDER_MENU_ITEM_ID = 3;
    public static final int VIEW_RECORDS_LIST = 0;
    public static final int VIEW_RECORD_TEXT = 1;
    public static final int VIEW_RECORD_FILES = 2;

    private DrawerLayout drawerLayout;
    private MultiLevelListView nodesListView;
    private NodesListAdapter nodesListAdapter;
    private RecordsListAdapter recordsListAdapter;
    private ListView recordsListView;
    private FilesListAdapter filesListAdapter;
    private ListView filesListView;
    private TetroidNode currentNode;
    private TetroidRecord currentRecord;
    private ViewFlipper viewFlipper;
    private WebView recordContentWebView;
    ToggleButton tbRecordFieldsExpander;
    ExpandableLayout expRecordFieldsLayout;
    TextView tvRecordTags;
    TextView tvRecordAuthor;
    TextView tvRecordUrl;
    TextView tvRecordDate;
    private int lastDisplayedViewId = 0;
    private boolean isAlreadyTryDecrypt = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/
        // панель
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        drawerLayout.openDrawer(GravityCompat.START);
        toggle.syncState();

        // список веток
        nodesListView = findViewById(R.id.nodes_list_view);
        nodesListView.setOnItemClickListener(onNodeClickListener);
        // список записей
        recordsListView = findViewById(R.id.records_list_view);
        recordsListView.setOnItemClickListener(onRecordClicklistener);
        TextView emptyTextView = findViewById(R.id.text_view_empty);
        recordsListView.setEmptyView(emptyTextView);
        registerForContextMenu(recordsListView);
        // список файлов
        filesListView = findViewById(R.id.files_list_view);
        filesListView.setOnItemClickListener(onFileClicklistener);
        emptyTextView = findViewById(R.id.files_text_view_empty);
        filesListView.setEmptyView(emptyTextView);

        viewFlipper = findViewById(R.id.view_flipper);
        recordContentWebView = findViewById(R.id.web_view_record_content);

        tvRecordTags = findViewById(R.id.text_view_record_tags);
        tvRecordAuthor = findViewById(R.id.text_view_record_author);
        tvRecordUrl = findViewById(R.id.text_view_record_url);
        tvRecordDate = findViewById(R.id.text_view_record_date);
        expRecordFieldsLayout = findViewById(R.id.layout_expander);
        tbRecordFieldsExpander = findViewById(R.id.toggle_button_expander);
        tbRecordFieldsExpander.setOnCheckedChangeListener(this);

        // инициализация
        SettingsManager.init(this);
        LogManager.init(this, SettingsManager.getLogPath(), SettingsManager.isWriteLog());
        LogManager.addLog(String.format(getString(R.string.app_start), Utils.getVersionName(this)));
        startInitStorage();
    }

    @Override
    public void onCheckedChanged(CompoundButton view, boolean isChecked) {
        expRecordFieldsLayout.toggle();
    }

    private void startInitStorage() {
        isAlreadyTryDecrypt = false;
        String storagePath = SettingsManager.getStoragePath();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (!checkReadExtStoragePermission()) {
                return;
            }
        }

        if (SettingsManager.isLoadLastStoragePath() && storagePath != null) {
            initStorage(storagePath);
        } else {
            showFolderChooser();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean checkReadExtStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            /*if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else*/ {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                        REQUEST_CODE_PERMISSION_REQUEST);
            }
            return false;
        }
        return true;
    }

    private void initStorage(String storagePath) {
        if (DataManager.init(storagePath)) {
            LogManager.addLog(getString(R.string.storage_settings_inited) + storagePath);
            drawerLayout.openDrawer(Gravity.LEFT);
            // сохраняем путь к хранилищу, если загрузили его в первый раз
            if (SettingsManager.isLoadLastStoragePath()) {
                SettingsManager.setStoragePath(storagePath);
            }

            // нужно ли выделять ветку, выбранную в прошлый раз
            // (обязательно после initListViews)
            TetroidNode nodeToSelect = null;
            String nodeId = SettingsManager.getSelectedNodeId();
            if (nodeId != null) {
                nodeToSelect = DataManager.getNode(nodeId);
                // если нашли, отображаем
//                if (nodeToSelect != null) {
//                    showNode(nodeToSelect);
//                }
            }

            // проверка зашифрованы ли данные
//            if (DataManager.isExistsCryptedNodes()) {
            if (DataManager.isCrypted()) {

                if (SettingsManager.getWhenAskPass().equals(getString(R.string.pref_when_ask_password_on_start))) {
                    // спрашивать пароль при старте
                    decryptStorage(nodeToSelect);
                    return;
                }
            }

            initStorage(nodeToSelect, false);

        } else {
            LogManager.addLog(R.string.storage_init_error, Toast.LENGTH_SHORT);
        }
    }

    /**
     * Вызывается при:
     * 1) запуске приложения, если есть зашифрованные ветки и установлен isAskPasswordOnStart
     * 2) запуске приложения, если выделение было сохранено на зашифрованной ветке
     * 3) при выделении зашифрованной ветки
     * @param node Ветка для выбора при удачной расшифровке
     */
    private void decryptStorage(TetroidNode node) {
        String middlePassHash;
        // пароль сохранен локально?
        if (SettingsManager.isSaveMiddlePassHashLocal()
                && (middlePassHash = SettingsManager.getMiddlePassHash()) != null) {
            // проверяем
            try {
                if (DataManager.checkMiddlePassHash(middlePassHash)) {
                    decryptStorage(middlePassHash, true, node);
                } else {
                    LogManager.addLog(R.string.wrong_saved_pass, Toast.LENGTH_LONG);
                    if (!isAlreadyTryDecrypt) {
                        isAlreadyTryDecrypt = true;
                        initStorage(node, false);
                    }
                }
            } catch (DataManager.EmptyFieldException e) {
                // if middle_hash_check_data field is empty, so asking "decrypt anyway?"
               ActivityDialogs.showEmptyPassCheckingFieldDialog(this, e.getFieldName(), node, new ActivityDialogs.IPassCheckResult() {
                   @Override
                   public void onApply(TetroidNode node) {
                       decryptStorage(SettingsManager.getMiddlePassHash(), true, node);
                   }
               });
            }
        } else {
            showPassDialog(node);
        }
    }

    void showPassDialog(final TetroidNode node) {
        LogManager.addLog(R.string.show_pass_dialog);
        // выводим окно с запросом пароля в асинхронном режиме
        ActivityDialogs.showPassDialog(this, node, new ActivityDialogs.IPassInputResult() {
            @Override
            public void applyPass(final String pass, TetroidNode node) {
                // подтверждение введенного пароля
                try {
                    if (DataManager.checkPass(pass)) {
                        String passHash = CryptManager.passToHash(pass);
                        // сохраняем хэш пароля
                        SettingsManager.setMiddlePassHash(passHash);

                        decryptStorage(pass, false, node);
                    } else {
                        LogManager.addLog(R.string.password_is_incorrect, Toast.LENGTH_LONG);
                        showPassDialog(node);
                    }
                } catch (DataManager.EmptyFieldException e) {

                    LogManager.addLog(e);

                    ActivityDialogs.showEmptyPassCheckingFieldDialog(MainActivity.this, e.getFieldName(), node, new ActivityDialogs.IPassCheckResult() {
                        @Override
                        public void onApply(TetroidNode node) {
                            decryptStorage(pass, false, node);
                            // пароль не сохраняем
                            // а спрашиваем нормально ли расшифровались данные, и потом сохраняем
                            // ...
                        }
                    });
                }
            }

            @Override
            public void cancelPass() {
                // загружаем хранилище как есть (только в первый раз, затем перезагружать не нужно)
                if (!isAlreadyTryDecrypt) {
                    isAlreadyTryDecrypt = true;
                    initStorage(node, false);
                }
            }
        });
    }

    private void decryptStorage(String pass, boolean isMiddleHash, TetroidNode nodeToSelect) {
        if (isMiddleHash)
            CryptManager.initFromMiddleHash(pass);
        else
            CryptManager.initFromPass(pass);
        initStorage(nodeToSelect, true);
    }

    private void initStorage(TetroidNode node, boolean isDecrypt) {
        if (isDecrypt && DataManager.isNodesExist()) {
            // расшифровываем зашифрованные ветки уже загруженного дерева
            if (DataManager.decryptAll()) {
                LogManager.addLog(R.string.storage_decrypted);
            } else {
                LogManager.addLog(getString(R.string.errors_during_decryption)
                    + (SettingsManager.isWriteLog()
                                ? getString(R.string.details_in_logs)
                                : getString(R.string.for_more_info_enable_log)),
                        Toast.LENGTH_LONG);
            }
            nodesListAdapter.notifyDataSetChanged();
        } else {
            // парсим дерево веток и расшифровываем зашифрованные
            if (DataManager.readStorage(isDecrypt)) {
                LogManager.addLog(R.string.storage_loaded);
            }
            // инициализация контролов
            initListViews();
        }
        // выбираем ветку в новом списке расшифрованных веток
        if (node != null)
            showNode(node);
    }

    private void initListViews() {
        // список веток
        this.nodesListAdapter = new NodesListAdapter(this, onNodeHeaderClickListener);
        nodesListView.setAdapter(nodesListAdapter);
        nodesListAdapter.setDataItems(DataManager.getRootNodes());
        // список записей
        this.recordsListAdapter = new RecordsListAdapter(this, onRecordAttachmentClickListener);
        recordsListView.setAdapter(recordsListAdapter);
        // список файлов
        this.filesListAdapter = new FilesListAdapter(this);
        filesListView.setAdapter(filesListAdapter);
    }

    void showFolderChooser() {
        Intent intent = new Intent(this, FolderPicker.class);
        intent.putExtra("title", getString(R.string.folder_chooser_title));
        intent.putExtra("location", SettingsManager.getStoragePath());
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SETTINGS_ACTIVITY) {
            if (SettingsManager.isAskReloadStorage) {
                SettingsManager.isAskReloadStorage = false;
                ActivityDialogs.showReloadStorageDialog(this, new ActivityDialogs.IReloadStorageResult() {
                    @Override
                    public void onApply() {
                        startInitStorage();
                    }
                    @Override
                    public void onCancel() {
                    }
                });
            }
        } else if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            String folderFullName = data.getStringExtra("data");
            initStorage(folderFullName);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startInitStorage();
                } else {
                    LogManager.addLog(R.string.missing_read_ext_storage_permissions, Toast.LENGTH_SHORT);
                }
            }
        }
    }

    /**
     * Отображение ветки => список записей
     * @param node
     */
    private void showNode(TetroidNode node)
    {
        // проверка нужно ли расшифровать ветку перед отображением
        if (!node.isNonCryptedOrDecrypted()) {
            decryptStorage(node);
            // выходим, т.к. возможен запрос пароля в асинхронном режиме
            return;
        }
        this.currentNode = node;
//        Toast.makeText(getApplicationContext(), node.getName(), Toast.LENGTH_SHORT).showPassDialog();
//        if (viewFlipper.getDisplayedChild() == VIEW_RECORD_TEXT)
//            viewFlipper.showPrevious();
        showView(VIEW_RECORDS_LIST);
        drawerLayout.closeDrawers();

        this.recordsListAdapter.reset(node.getRecords());
        recordsListView.setAdapter(recordsListAdapter);
//        setTitle(node.getName());
//        Toast.makeText(this, "Открытие " + node.getName(), Toast.LENGTH_SHORT).showPassDialog();
    }

    /**
     * Отображение записи
     * @param position Индекс записи в списке записей ветки
     */
    private void showRecord(int position) {
        TetroidRecord record = currentNode.getRecords().get(position);
        showRecord(record);
    }

    /**
     * Отображение записи
     * @param record Запись
     */
    private void showRecord(final TetroidRecord record) {
        this.currentRecord = record;
        LogManager.addLog("Чтение записи: " + record.getDirName());
        String text = DataManager.getRecordTextDecrypted(record);
        if (text == null) {
            LogManager.addLog("Ошибка чтения записи", Toast.LENGTH_LONG);
            return;
        }
        recordContentWebView.loadDataWithBaseURL(DataManager.getRecordDirUri(record),
                text, "text/html", "UTF-8", null);
//            recordContentWebView.loadUrl(recordContentUrl);
        recordContentWebView.setWebViewClient(new WebViewClient() {
            /*@Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return super.shouldOverrideUrlLoading(view, request);
            }*/
            @Override
            public void onPageFinished(WebView view, String url) {
                tvRecordTags.setText(record.getTags());
                tvRecordAuthor.setText(record.getAuthor());
                tvRecordUrl.setText(record.getUrl());
                tvRecordDate.setText(record.getCreatedString(SettingsManager.getDateFormatString()));
                showView(VIEW_RECORD_TEXT);
            }
        });
    }

    /**
     * Отображение списка прикрепленных файлов
     * @param position Индекс записи в списке записей ветки
     */
    private void showFilesList(int position) {
        TetroidRecord record = currentNode.getRecords().get(position);
        showFilesList(record);
    }

    /**
     * Отображение списка прикрепленных файлов
     * @param record Запись
     */
    private void showFilesList(TetroidRecord record) {
        this.currentRecord = record;
        showView(VIEW_RECORD_FILES);
        this.filesListAdapter.reset(record);
        filesListView.setAdapter(filesListAdapter);
//        setTitle(record.getName());
    }

    /**
     * Открытие прикрепленного файла
     * @param position Индекс файла в списке прикрепленных файлов записи
     */
    private void openFile(int position) {
        if (currentRecord.isCrypted() && !SettingsManager.isDecryptFilesInTemp()) {
            LogManager.addLog(R.string.viewing_decrypted_not_possible, Toast.LENGTH_LONG);
            return;
        }
        TetroidFile file = currentRecord.getAttachedFiles().get(position);
        DataManager.openFile(this, currentRecord, file);
    }

    private void openRecordFolder(int position) {
        TetroidRecord record = currentNode.getRecords().get(position);
        openFolder(DataManager.getRecordDirUri(record));
    }

    public void openFolder(String pathUri){
        Uri uri = Uri.parse(pathUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "resource/folder");
        if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
            startActivity(intent);
        } else {
            LogManager.addLog("Отсутствует файл менеджер", Toast.LENGTH_LONG);
        }
    }

    /**
     * Обработчик клика на заголовке ветки с подветками
     */
    NodesListAdapter.OnNodeHeaderClickListener onNodeHeaderClickListener = new NodesListAdapter.OnNodeHeaderClickListener() {
        @Override
        public void onClick(TetroidNode node) {
            showNode(node);
        }
    };

    /**
     * Обработчик клика на иконке прикрепленных файлов записи
     */
    RecordsListAdapter.OnRecordAttachmentClickListener onRecordAttachmentClickListener = new RecordsListAdapter.OnRecordAttachmentClickListener() {
        @Override
        public void onClick(TetroidRecord record) {
            showFilesList(record);
        }
    };

    /**
     * Обработчик клика на "конечной" ветке (без подветок)
     */
    private OnItemClickListener onNodeClickListener = new OnItemClickListener() {

        /**
         * Клик на конечной ветке
         */
        @Override
        public void onItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
            showNode((TetroidNode)item);
        }

        /**
         * Клик на родительской ветке
         */
        @Override
        public void onGroupItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
            // это событие обрабатывается с помощью OnNodeHeaderClickListener, чтобы разделить клик
            // на заголовке и на стрелке раскрытия/закрытия ветки
            TetroidNode node = (TetroidNode) item;
            if (!node.isNonCryptedOrDecrypted()) {
                decryptStorage(node);
                // как остановить дальнейшее выполнение, чтобы не стабатывал Expander?
                return;
            }
        }
    };

    /**
     * Обработчик клика на записи
     */
    private AdapterView.OnItemClickListener onRecordClicklistener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            showRecord(position);
        }
    };

    /**
     * Обработчик клика на прикрепленном файле
     */
    private AdapterView.OnItemClickListener onFileClicklistener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            openFile(position);
        }
    };

    /**
     *
     * @param viewId
     */
    void showView(int viewId) {
        lastDisplayedViewId = viewFlipper.getDisplayedChild();
        viewFlipper.setDisplayedChild(viewId);
        if (viewId == VIEW_RECORDS_LIST) {
            setTitle((currentNode != null) ? currentNode.getName() : "");
        } else if (viewId == VIEW_RECORD_TEXT || viewId == VIEW_RECORD_FILES) {
            setTitle((currentRecord != null) ? currentRecord.getName() : "");
        }
    }

    /**
     * Обработчик нажатия кнопки Назад
     */
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (viewFlipper.getDisplayedChild() == VIEW_RECORD_TEXT) {
            showView(VIEW_RECORDS_LIST);
        } else if (viewFlipper.getDisplayedChild() == VIEW_RECORD_FILES) {
            // смотрим какая страница была перед этим
            if (lastDisplayedViewId == VIEW_RECORD_TEXT)
                showView(VIEW_RECORD_TEXT);
            else
                showView(VIEW_RECORDS_LIST);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Обработчик создания системного меню
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /**
     * Обработчик выбора пунктов системного меню
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showActivityForResult(SettingsActivity.class, REQUEST_CODE_SETTINGS_ACTIVITY);
            return true;
        } else if (id == R.id.action_about) {
            showActivity(this, AboutActivity.class);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Обработчик создания контекстного меню при долгом тапе на записи
     * @param menu
     * @param v
     * @param menuInfo
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.add(Menu.NONE, OPEN_RECORD_MENU_ITEM_ID, Menu.NONE, R.string.show_record_content);
        menu.add(Menu.NONE, SHOW_FILES_MENU_ITEM_ID, Menu.NONE, R.string.show_attached_files);
        menu.add(Menu.NONE, OPEN_RECORD_FOLDER_MENU_ITEM_ID, Menu.NONE, R.string.open_record_folder);
    }

    /**
     * Обработчик выбора пунктов контекстного меню записи
     * @param item
     * @return
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case OPEN_RECORD_MENU_ITEM_ID:
                showRecord(info.position);
                return true;
            case SHOW_FILES_MENU_ITEM_ID:
                showFilesList(info.position);
                return true;
            case OPEN_RECORD_FOLDER_MENU_ITEM_ID:
                openRecordFolder(info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public static void showActivity(Context context, Class<?> cls) {
        Intent intent = new Intent(context, cls);
        context.startActivity(intent);
    }

    public void showActivityForResult(Class<?> cls, int requestCode) {
        Intent intent = new Intent(this, cls);
        startActivityForResult(intent, requestCode);
    }
}
