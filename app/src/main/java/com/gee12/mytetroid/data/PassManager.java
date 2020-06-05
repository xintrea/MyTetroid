package com.gee12.mytetroid.data;

import android.content.Context;
import android.widget.Toast;

import com.gee12.mytetroid.LogManager;
import com.gee12.mytetroid.R;
import com.gee12.mytetroid.TaskStage;
import com.gee12.mytetroid.TetroidLog;
import com.gee12.mytetroid.crypt.Base64;
import com.gee12.mytetroid.crypt.CryptManager;
import com.gee12.mytetroid.crypt.Crypter;
import com.gee12.mytetroid.model.TetroidNode;
import com.gee12.mytetroid.utils.Utils;
import com.gee12.mytetroid.views.AskDialogs;

public class PassManager extends DataManager {

    /**
     * Проверка введенного пароля с сохраненным проверочным хэшем.
     * @param pass
     * @return
     * @throws DatabaseConfig.EmptyFieldException
     */
    public static boolean checkPass(String pass) throws DatabaseConfig.EmptyFieldException {
        String salt = databaseConfig.getCryptCheckSalt();
        String checkhash = databaseConfig.getCryptCheckHash();
        return CryptManager.checkPass(pass, salt, checkhash);
    }

    /**
     * Проверка сохраненного хэша пароля с помощью сохраненных зашифрованных данных.
     * @param passHash
     * @return
     * @throws DatabaseConfig.EmptyFieldException
     */
    public static boolean checkMiddlePassHash(String passHash) throws DatabaseConfig.EmptyFieldException {
        String checkdata = databaseConfig.getMiddleHashCheckData();
        return CryptManager.checkMiddlePassHash(passHash, checkdata);
    }

    /**
     * Каркас проверки введенного пароля.
     * @param context
     * @param pass
     * @param callback
     * @param wrongPassRes
     */
    public static boolean checkPass(Context context, String pass, DataManager.ICallback callback, int wrongPassRes) {
        try {
            if (checkPass(pass)) {
                callback.run();
            } else {
                LogManager.log(wrongPassRes, Toast.LENGTH_LONG);
                return false;
            }
        } catch (DatabaseConfig.EmptyFieldException ex) {
            // если поля в INI-файле для проверки пустые
            LogManager.log(ex);
            // спрашиваем "continue anyway?"
            AskDialogs.showEmptyPassCheckingFieldDialog(context, ex.getFieldName(), new AskDialogs.IApplyCancelResult() {
                @Override
                public void onApply() {
                    // TODO: тут спрашиваем нормально ли расшифровались данные
                    //  ...
                    if (callback != null) {
                        callback.run();
                    }
                }
                @Override
                public void onCancel() {
                }
            });
        }
        return true;
    }

    /**
     * Сохранение пароля в настройках и его установка для шифрования.
     * @param pass
     */
    public static void initPass(String pass) {
        String passHash = CryptManager.passToHash(pass);
        if (SettingsManager.isSaveMiddlePassHashLocal()) {
            // сохраняем хэш пароля
            SettingsManager.setMiddlePassHash(passHash);
            // записываем проверочную строку
            saveMiddlePassCheckData(passHash);
        } else {
            // сохраняем хэш пароля в оперативную память, может еще понадобится
            CryptManager.setMiddlePassHash(passHash);
        }
        // здесь, по идее, можно сохранять сразу passHash (с параметром isMiddleHash=true),
        // но сделал так
        DataManager.initCryptPass(pass, false);
    }

    /**
     * Установка пароля хранилища впервые.
     * @return
     */
    public static void setupPass(Context context) {
        LogManager.log(R.string.log_start_pass_setup);
        // вводим пароль
        AskDialogs.showPassEnterDialog(context, null, true, new AskDialogs.IPassInputResult() {
            @Override
            public void applyPass(String pass, TetroidNode node) {
                setupPass(pass);
            }
            @Override
            public void cancelPass() {
            }
        });
    }

    /**
     * Установка пароля хранилища впервые.
     * @param pass
     */
    public static void setupPass(String pass) {
        // сохраняем в database.ini
        if (savePassCheckData(pass)) {
            LogManager.log(R.string.log_pass_setted, LogManager.Types.INFO, Toast.LENGTH_SHORT);
            initPass(pass);
        } else {
            LogManager.log(R.string.log_pass_set_error, LogManager.Types.ERROR, Toast.LENGTH_LONG);
        }
    }

    /**
     * Смена пароля хранилища.
     * @return
     */
/*    public static void changePass(Context context, ITaskProgress taskProgress) {
        LogManager.log(R.string.log_start_pass_change);
        // вводим пароли (с проверкой на пустоту и равенство)
        AskDialogs.showPassChangeDialog(context, (curPass, newPass) -> {
            // проверяем пароль
            return checkPass(context, curPass, () -> {
                if (changePass(curPass, newPass, taskProgress)) {
                    LogManager.log(R.string.log_pass_changed, LogManager.Types.INFO, Toast.LENGTH_SHORT);
                } else {
                    LogManager.log(R.string.log_pass_change_error, LogManager.Types.INFO, Toast.LENGTH_SHORT);
                }
            }, R.string.log_cur_pass_is_incorrect);
        });
    }*/

    /**
     * Смена пароля хранилища.
     * @return
     */
    /*public static boolean changePass(String curPass, String newPass, ITaskProgress taskProgress) {
        // сначала устанавливаем текущий пароль
        initPass(curPass);
        // и расшифровываем хранилище
        taskProgress.nextStage(context.getString(R.string.stage_decrypt_old_pass));
        if (DataManager.decryptStorage(true)) {
            TetroidLog.logOperRes(TetroidLog.Objs.STORAGE, TetroidLog.Opers.DECRYPT);
        } else {
            TetroidLog.logDuringOperErrors(TetroidLog.Objs.STORAGE, TetroidLog.Opers.DECRYPT, Toast.LENGTH_LONG);
            return false;
        }
        // теперь устанавливаем новый пароль
        initPass(newPass);
        // и перешифровываем зашифрованные ветки
        taskProgress.nextStage(context.getString(R.string.stage_reencrypt_new_pass));
        if (DataManager.reencryptStorage()) {
            TetroidLog.logOperRes(TetroidLog.Objs.STORAGE, TetroidLog.Opers.REENCRYPT);
        } else {
            TetroidLog.logDuringOperErrors(TetroidLog.Objs.STORAGE, TetroidLog.Opers.REENCRYPT, Toast.LENGTH_LONG);
            return false;
        }
        // сохраняем mytetra.xml
        taskProgress.nextStage(context.getString(R.string.stage_save_storage));
        if (DataManager.saveStorage()) {
            LogManager.log(R.string.log_mytetra_xml_was_saved);
        } else {
            LogManager.log(R.string.log_mytetra_xml_saving_error, LogManager.Types.ERROR, Toast.LENGTH_LONG);
        }
        // сохраняем данные в database.ini
        savePassCheckData(newPass);
        return true;
    }*/
    public static boolean changePass(String curPass, String newPass, ITaskProgress taskProgress) {
        // сначала устанавливаем текущий пароль
        taskProgress.nextStage(TetroidLog.Objs.CUR_PASS, TetroidLog.Opers.SET, TaskStage.Stages.START);
        initPass(curPass);
        // и расшифровываем хранилище
        if (!taskProgress.nextStage(TetroidLog.Objs.STORAGE, TetroidLog.Opers.DECRYPT, () ->
                DataManager.decryptStorage(true)))
            return false;
        // теперь устанавливаем новый пароль
        taskProgress.nextStage(TetroidLog.Objs.NEW_PASS, TetroidLog.Opers.SET, TaskStage.Stages.START);
        initPass(newPass);
        // и перешифровываем зашифрованные ветки
        if (!taskProgress.nextStage(TetroidLog.Objs.STORAGE, TetroidLog.Opers.REENCRYPT, () ->
                DataManager.reencryptStorage()))
            return false;
        // сохраняем mytetra.xml
        taskProgress.nextStage(TetroidLog.Objs.STORAGE, TetroidLog.Opers.SAVE, () ->
                DataManager.saveStorage());
        // сохраняем данные в database.ini
        taskProgress.nextStage(TetroidLog.Objs.NEW_PASS, TetroidLog.Opers.SAVE, TaskStage.Stages.START);
        savePassCheckData(newPass);
        return true;
    }

    /**
     * Сброс сохраненного хэша пароля и его проверочных данных.
     */
    public static void clearSavedPass() {
        SettingsManager.setMiddlePassHash(null);
        CryptManager.setMiddlePassHash(null);
        clearPassCheckData();
        clearMiddlePassCheckData();
    }

    /**
     * Сохранение проверочного хэша пароля и сопутствующих данных в database.ini.
     * @param newPass
     * @return
     */
    public static boolean savePassCheckData(String newPass) {
        byte[] salt = Utils.createRandomBytes(32);
        byte[] passHash = null;
        try {
            passHash = Crypter.calculatePBKDF2Hash(newPass, salt);
        } catch (Exception ex) {
            LogManager.log(ex);
            return false;
        }
        return databaseConfig.savePass(Base64.encodeToString(passHash, false),
                Base64.encodeToString(salt, false), true);
    }

    /**
     * Сохранение проверочной строки промежуточного хэша пароля в database.ini.
     * @param passHash
     * @return
     */
    public static boolean saveMiddlePassCheckData(String passHash) {
        String checkData = Crypter.createMiddlePassHashCheckData(passHash);
        return databaseConfig.saveCheckData(checkData);
    }

    /**
     * Очистка сохраненного проверочнго хэша пароля.
     * @return
     */
    public static boolean clearPassCheckData() {
        SettingsManager.setMiddlePassHash(null);
        return databaseConfig.savePass(null, null, false);
    }

    /**
     * Очистка сохраненной проверочной строки промежуточного хэша пароля.
     * @return
     */
    public static boolean clearMiddlePassCheckData() {
        return databaseConfig.saveCheckData(null);
    }

}
