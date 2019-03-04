package com.gee12.mytetroid.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.ContextMenu;
import android.view.View;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.gee12.mytetroid.R;
import com.gee12.mytetroid.SettingsManager;
import com.gee12.mytetroid.UriUtil;
import com.gee12.mytetroid.crypt.CryptManager;
import com.gee12.mytetroid.data.DataManager;
import com.gee12.mytetroid.data.TetroidFile;
import com.gee12.mytetroid.data.TetroidNode;
import com.gee12.mytetroid.data.TetroidRecord;
import com.gee12.mytetroid.views.FilesListAdapter;
import com.gee12.mytetroid.views.NodesListAdapter;
import com.gee12.mytetroid.views.PassInputDialog;
import com.gee12.mytetroid.views.RecordsListAdapter;

//import net.rdrei.android.dirchooser.DirectoryChooserActivity;
//import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import java.net.URLDecoder;

import lib.folderpicker.FolderPicker;
import pl.openrnd.multilevellistview.ItemInfo;
import pl.openrnd.multilevellistview.MultiLevelListView;
import pl.openrnd.multilevellistview.OnItemClickListener;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    public static final int FILE_BROWSE = 1;
    public static final int GET_CONTENT = 2;
    public static final int OPEN_DOC = 3;

    public static final int OPEN_RECORD_MENU_ITEM_ID = 1;
    public static final int SHOW_FILES_MENU_ITEM_ID = 2;
    public static final int VIEW_RECORDS_LIST = 0;
    public static final int VIEW_RECORD_TEXT = 1;
    public static final int VIEW_RECORD_FILES = 2;

    private DrawerLayout drawerLayout;
    private MultiLevelListView nodesListView;
    private RecordsListAdapter recordsListAdapter;
    private ListView recordsListView;
    private FilesListAdapter filesListAdapter;
    private ListView filesListView;
    private TetroidNode currentNode;
    private TetroidRecord currentRecord;
    private ViewFlipper viewFlipper;
//    private TextView recordContentTextView;
    private WebView recordContentWebView;
    private int lastDisplayedViewId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        // панель
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        drawerLayout.openDrawer(GravityCompat.START);
        toggle.syncState();

        // список веток
        nodesListView = (MultiLevelListView) findViewById(R.id.nodes_list_view);
        nodesListView.setOnItemClickListener(onNodeClickListener);
        // список записей
        recordsListView = (ListView)findViewById(R.id.records_list_view);
        recordsListView.setOnItemClickListener(onRecordClicklistener);
        TextView emptyTextView = (TextView)findViewById(R.id.text_view_empty);
        recordsListView.setEmptyView(emptyTextView);
        registerForContextMenu(recordsListView);
        // список файлов
        filesListView = (ListView)findViewById(R.id.files_list_view);
        filesListView.setOnItemClickListener(onFileClicklistener);
        emptyTextView = (TextView)findViewById(R.id.files_text_view_empty);
        filesListView.setEmptyView(emptyTextView);

        viewFlipper = (ViewFlipper) findViewById(R.id.view_flipper);
//        recordContentTextView = (TextView) findViewById(R.id.text_view_record_content);
        recordContentWebView = (WebView)findViewById(R.id.web_view_record_content);

        // загружаем данные
        SettingsManager.init(this);
        startInitStorage();
    }

    private void startInitStorage() {
        String storagePath = SettingsManager.getStoragePath();
//        String storagePath = "net://Иван Бондарь-687:@gdrive/MyTetraData";
        if (SettingsManager.isLoadLastStoragePath() && storagePath != null) {
            initStorage(storagePath);
        } else {
            showChooser3();
        }
    }

    private void initStorage(String storagePath) {
        if (DataManager.init(storagePath)) {
            if (SettingsManager.isLoadLastStoragePath())
                SettingsManager.setStoragePath(storagePath);
            // проверка зашифрованы ли данные
            if (DataManager.isExistsCryptedNodes()) {

                if (SettingsManager.getWhenAskPass().equals(getString(R.string.pref_when_ask_password_on_start))) {
                    // спрашивать пароль при старте
                    decryptStorage(null);
                } /*else {
                    // спрашивать пароль при выборе зашифрованной ветки
                    String nodeId = SettingsManager.getSelectedNodeId();
                    if (nodeId != null) {
                        TetroidNode node = DataManager.getNode(nodeId);
                        // если нашли, отображаем
                        if (node != null) {
                            if (node.isNonCryptedOrDecrypted()) {
                                decryptStorage(node);
                            }
//                            showNode(node);
                        }
                    }
                }*/
            }

            initListViews();

            // нужно ли выделять ветку, выбранную в прошлый раз
            // (обязательно после initListViews)
            String nodeId = SettingsManager.getSelectedNodeId();
            if (nodeId != null) {
                TetroidNode node = DataManager.getNode(nodeId);
                // если нашли, отображаем
                if (node != null) {
                    showNode(node);
                }
            }
        } else {
            Toast.makeText(this, "Ошибка инициализации хранилища", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Вызывается при:
     * 1) запуске приложения, если есть зашифрованные ветки и установлен isAskPasswordOnStart
     * 2) запуске приложения, если выделение было сохранено на зашифрованной ветке
     * 3) при выделении зашифрованной ветки
     * @param node Ветка для расшифровки или null, если нужно расшифровать всю коллекцию
     * @param node Если ветка передана, значит нужно вызвать showNode() при удачной расшифровке
     */
    private void decryptStorage(TetroidNode node) {
        // пароль сохранен локально?
        if (SettingsManager.isSavePasswordHashLocal()) {
            // достаем хэш пароля
//            String pass = "iHMy5~sv62";
            String pass = SettingsManager.getPassHash();
            // проверяем
            if (CryptManager.check(pass)) {
                decryptStorage(pass, node);
            } else {
                Toast.makeText(this, "Неверный сохраненный пароль", Toast.LENGTH_LONG).show();
            }
        } else {
            // выводим окно с запросом пароля в асинхронном режиме
            PassInputDialog.showPassDialog(this, node, new PassInputDialog.IPassInputResult() {
                @Override
                public void applyPass(String pass, TetroidNode node) {
                    // подтверждение введенного пароля
                    if (CryptManager.check(pass)) {

                        String passHash = CryptManager.getPassHash(pass);
                        // сохраняем хэш пароля
                        SettingsManager.setPassHash(passHash);

                        decryptStorage(pass, node);
                    } else {
                        Toast.makeText(MainActivity.this, "Введен неверный пароль", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void decryptStorage(String pass, TetroidNode node) {
        CryptManager.init(pass);
        DataManager.decryptAll();
        // выбираем ветку
        showNode(node);
    }

//    private void askPasswordReturn(String pass, TetroidNode node) {
//        // получаем пароль
//        CryptManager.init(pass);
//
//        if (node != null) {
//            // попытка открытия ветки
//            CryptManager.decryptNode(node);
//        } else {
//            // попытка прочтения всей базы
//            DataManager.decryptAll();
//        }
//    }

    private void initListViews() {
        // список веток
        NodesListAdapter listAdapter = new NodesListAdapter(this, onNodeHeaderClickListener);
        nodesListView.setAdapter(listAdapter);
        listAdapter.setDataItems(DataManager.getRootNodes());
        // список записей
        this.recordsListAdapter = new RecordsListAdapter(this, onRecordAttachmentClickListener);
        // список файлов
        this.filesListAdapter = new FilesListAdapter(this);
    }

    void showChooser1() {
        //            if (StorageAF.useStorageFramework(FileSelectActivity.this)) {
        if (false) {
            Intent intent = new Intent(StorageChooserActivity.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
//                intent.setType("text/*");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, OPEN_DOC);
        }
        else {
            Intent intent;
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
//                intent.setType("text/*");

            try {
                startActivityForResult(intent, GET_CONTENT);
            } catch (ActivityNotFoundException e) {
//                    lookForOpenIntentsFilePicker();
            } catch (SecurityException e) {
//                    lookForOpenIntentsFilePicker();
            }
        }
    }

//    static final int REQUEST_DIRECTORY = 222;
//    void showChooser2() {
//        final Intent chooserIntent = new Intent(this, DirectoryChooserActivity.class);
//
//        final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
//                .newDirectoryName("DirChooserSample")
//                .allowReadOnlyDirectory(true)
//                .allowNewDirectoryNameModification(true)
//                .build();
//
//        chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_CONFIG, config);
////        chooserIntent.putExtra(DirectoryChooserActivity.EXTRA_NEW_DIR_NAME, "Snapprefs");
//
//        // REQUEST_DIRECTORY is a constant integer to identify the request, e.g. 0
//        startActivityForResult(chooserIntent, REQUEST_DIRECTORY);
//    }

    static final int FOLDERPICKER_CODE = 333;
    void showChooser3() {
        Intent intent = new Intent(this, FolderPicker.class);
        startActivityForResult(intent, FOLDERPICKER_CODE);
    }

//    private void lookForOpenIntentsFilePicker() {
//
////        if (Interaction.isIntentAvailable(FileSelectActivity.this, Intents.OPEN_INTENTS_FILE_BROWSE)) {
//        if (true) {
//            Intent i = new Intent(Intents.OPEN_INTENTS_FILE_BROWSE);
//            i.setData(Uri.parse("file://" + Util.getEditText(FileSelectActivity.this, R.id.file_filename)));
//            try {
//                startActivityForResult(i, FILE_BROWSE);
//            } catch (ActivityNotFoundException e) {
////                showBrowserDialog();
//            }
//
//        } else {
////            showBrowserDialog();
//        }
//    }

//    private void showBrowserDialog() {
//        BrowserDialog diag = new BrowserDialog(FileSelectActivity.this);
//        diag.showPassDialog();
//    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

//        fillData();

        String fileName = null;
        if (requestCode == FILE_BROWSE && resultCode == RESULT_OK) {
            fileName = data.getDataString();
            if (fileName != null) {
                if (fileName.startsWith("file://")) {
                    fileName = fileName.substring(7);
                }

                fileName = URLDecoder.decode(fileName);
            }

        }
        else if ((requestCode == GET_CONTENT || requestCode == OPEN_DOC) && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
//                    if (StorageAF.useStorageFramework(this)) {
                    if (false) {
                        try {
                            // try to persist read and write permissions
                            ContentResolver resolver = getContentResolver();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
//                                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            }
                        } catch (Exception e) {
                            // nop
                        }
                    }
                    if (requestCode == GET_CONTENT) {
                        uri = UriUtil.translate(this, uri);
                    }
                    fileName = uri.toString();
                }
            }
//        } else if (requestCode == REQUEST_DIRECTORY) {
//            if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
//                fileName = (data.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
//            } else {
//                // Nothing selected
//            }
        } else if (requestCode == FOLDERPICKER_CODE && resultCode == Activity.RESULT_OK) {

            fileName = data.getExtras().getString("data");
        }

        if (fileName != null) {
            // выбор файла mytetra.xml
//            File file = new File(fileName);
//            String path = file.getParent();

            // 1
//            Uri uri = UriUtil.parseDefaultFile(fileName);
//            String scheme = uri.getScheme();
//
//            if (!EmptyUtils.isNullOrEmpty(scheme) && scheme.equalsIgnoreCase("file")) {
//                File dbFile = new File(uri.getPath());
//                if (!dbFile.exists()) {
////                    throw new FileNotFoundException();
//                    return;
//                }
//                String path = dbFile.getParent();
//                initListViews(path);
//            }
            // 2
            initStorage(fileName);
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
    private void showRecord(TetroidRecord record) {
        this.currentRecord = record;
//        String recordContentUrl = record.getRecordTextUrl(DataManager.getStoragePath(), DataManager.getTempPath());
        String recordContentUrl = DataManager.getRecordTextUrl(record);
        if (recordContentUrl != null) {
            recordContentWebView.setVisibility(View.INVISIBLE);
            recordContentWebView.loadData(DataManager.getRecordTextDecrypted(record), "text/html; charset=UTF-8", null);
//            recordContentWebView.loadUrl(recordContentUrl);
            recordContentWebView.setWebViewClient(new WebViewClient() {
                /*@Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return super.shouldOverrideUrlLoading(view, request);
                }*/
                @Override
                public void onPageFinished(WebView view, String url) {
                    showView(VIEW_RECORD_TEXT);
                    recordContentWebView.setVisibility(View.VISIBLE);
                }
            });
        }
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
        this.filesListAdapter.reset(record.getAttachedFiles());
        filesListView.setAdapter(filesListAdapter);
//        setTitle(record.getName());
    }

    /**
     * Открытие прикрепленного файла
     * @param position Индекс файла в списке прикрепленных файлов записи
     */
    private void openFile(int position) {
        TetroidFile file = currentRecord.getAttachedFiles().get(position);
        openFile(file);
    }

    /**
     * Открытие прикрепленного файла
     * @param file Файл
     */
    private void openFile(TetroidFile file) {
        Toast.makeText(this, "Открытие файла " + file.getFileName(), Toast.LENGTH_SHORT).show();
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
     * Обработчик клика на заголовке ветки с подветками
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

        @Override
        public void onItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
            showNode((TetroidNode)item);
        }

        @Override
        public void onGroupItemClicked(MultiLevelListView parent, View view, Object item, ItemInfo itemInfo) {
            // это событие обрабатывается с помощью OnNodeHeaderClickListener, чтобы разделить клик
            // на заголовке и на стрелке раскрытия/закрытия ветки
            if (!((TetroidNode)item).isNonCryptedOrDecrypted()) {
                Toast.makeText(MainActivity.this, "Нужно ввести пароль", Toast.LENGTH_SHORT).show();
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
     * Обработчик клика на файле
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
            setTitle(currentNode.getName());
        } else if (viewId == VIEW_RECORD_TEXT || viewId == VIEW_RECORD_FILES) {
            setTitle(currentRecord.getName());
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
        // Inflate the menu; this adds items to the action bar if it is present.
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showActivity(this, SettingsActivity.class);
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

        menu.add(Menu.NONE, OPEN_RECORD_MENU_ITEM_ID, Menu.NONE, "Открыть");
        menu.add(Menu.NONE, SHOW_FILES_MENU_ITEM_ID, Menu.NONE, "Файлы");
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
            default:
                return super.onContextItemSelected(item);
        }
    }

    public static void showActivity(Context context, Class<?> cls) {
        Intent intent = new Intent(context, cls);
        context.startActivity(intent);
    }
}
