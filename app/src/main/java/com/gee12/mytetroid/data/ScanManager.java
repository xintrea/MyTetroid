package com.gee12.mytetroid.data;

import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Поиск объектов хранилища.
 * Название такое, а не SearchManager, из-за существования одноименного класса в пакете android.app.
 */
public class ScanManager implements Parcelable {

    public static final String QUERY_SEPAR = " ";
//    private DataManager dataManager;
    /**
     * Запрос
     */
    private String query;
    /**
     * Источники поиска
     */
    boolean inText;
    boolean inRecordsNames;
    boolean inAuthor;
    boolean inUrl;
    boolean inTags;
    boolean inNodes;
    boolean inFiles;
    /**
     * Разбивать ли запрос на слова
     */
    private boolean isSplitToWords;
    /**
     * Искать только целые слова
     */
    private boolean isOnlyWholeWords;
    /**
     * Искать только в текущей ветке
     */
    private boolean isSearchInNode;


    public ScanManager(String query) {
        this.query = query;
    }

    public ScanManager(String query, boolean isSplitToWords, boolean isOnlyWholeWords, boolean isSearchInNode) {
        this.query = query;
        this.isSplitToWords = isSplitToWords;
        this.isOnlyWholeWords = isOnlyWholeWords;
        this.isSearchInNode = isSearchInNode;
    }

    protected ScanManager(Parcel in) {
        this.query = in.readString();
        this.isSplitToWords = in.readInt() == 1;
        this.isOnlyWholeWords = in.readInt() == 1;
        this.isSearchInNode = in.readInt() == 1;
    }

    public List<FoundObject> globalSearch(/*DataManager data, */TetroidNode node) {
        List<FoundObject> res = new ArrayList<>();

        if (isSplitToWords) {
            for (String word : query.split(QUERY_SEPAR)) {
                res.addAll(globalSearch(/*data, */node, word));
            }
        } else {
            res.addAll(globalSearch(/*data, */node, query));
        }
        return res;
    }

    public List<FoundObject> globalSearch(/*DataManager data, */TetroidNode node, String query) {
        List<FoundObject> res = new ArrayList<>();

        List<TetroidNode> srcNodes;
        if (isSearchInNode && node != null) {
            srcNodes = new ArrayList<>();
            srcNodes.add(node);
        } else {
            srcNodes = DataManager.getRootNodes();
        }

        boolean inRecords = inRecordsNames || inText || inAuthor || inUrl || inFiles;
        if (inNodes || inRecords) {
            res.addAll(searchInNodes(srcNodes, query, isOnlyWholeWords, inRecords));
        }

        if (inTags && !isSearchInNode) {
            res.addAll(searchInNodes(srcNodes, query, isOnlyWholeWords, inRecords));
        }
        return res;
    }

    /**
     * Поиск по названиям веток.
     * Пропускает зашифрованные ветки.
     * @param nodes
     * @param query
     * @return
     */
    public static List<TetroidNode> searchInNodesNames(
            List<TetroidNode> nodes, String query, boolean isOnlyWholeWords) {
        List<TetroidNode> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidNode node : nodes) {
            if (!node.isNonCryptedOrDecrypted())
                continue;
            if (node.getName().matches(regex)) {
                node.addFoundType(FoundObject.TYPE_NODE);
                found.add(node);
            }
            if (node.getSubNodesCount() > 0) {
                found.addAll(searchInNodesNames(node.getSubNodes(), query, isOnlyWholeWords));
            }
        }
        return found;
    }

    public List<FoundObject> searchInNodes(List<TetroidNode> nodes, String query,
            boolean isOnlyWholeWords, boolean inRecords) {
        List<FoundObject> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidNode node : nodes) {
            if (!node.isNonCryptedOrDecrypted())
                continue;
            if (inNodes && node.getName().matches(regex)) {
                node.addFoundType(FoundObject.TYPE_NODE);
                found.add(node);
            }
            if (inRecords && node.getRecordsCount() > 0) {
                found.addAll(searchInRecords(node.getRecords(), query, isOnlyWholeWords));
            }
            if (node.getSubNodesCount() > 0) {
                found.addAll(searchInNodes(node.getSubNodes(), query, isOnlyWholeWords, inRecords));
            }
        }
        return found;
    }

    /**
     * Поиск по названиям записей.
     * @param srcRecords
     * @param query
     * @return
     */
    public List<FoundObject> searchInRecords(
            List<TetroidRecord> srcRecords, String query, boolean isOnlyWholeWords) {
        List<FoundObject> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidRecord record : srcRecords) {
            if (inRecordsNames && record.getName().matches(regex)) {
                record.addFoundType(FoundObject.TYPE_RECORD_NAME);
                found.add(record);
            }
            if (inAuthor && record.getAuthor().matches(regex)) {
                record.addFoundType(FoundObject.TYPE_AUTHOR);
                found.add(record);
            }
            if (inUrl && record.getAuthor().matches(regex)) {
                record.addFoundType(FoundObject.TYPE_URL);
                found.add(record);
            }
            if (inFiles && record.getAttachedFilesCount() > 0) {
                found.addAll(searchInFiles(record.getAttachedFiles(), query, isOnlyWholeWords));
            }
            if (inText) {
                String text = DataManager.getRecordTextDecrypted(record);
                if (text.matches(regex)) {
                    record.addFoundType(FoundObject.TYPE_RECORD_TEXT);
                    found.add(record);
                }
            }
            if (inTags && isSearchInNode) {

            }
        }
        return found;
    }

    public static List<TetroidRecord> searchInRecordsNames(
            List<TetroidRecord> srcRecords, String query, boolean isOnlyWholeWords) {
        List<TetroidRecord> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidRecord record : srcRecords) {
            if (record.getName().matches(regex)) {
                found.add(record);
            }
        }
        return found;
    }

    public static List<FoundObject> searchInFiles(
            List<TetroidFile> srcFiles, String query, boolean isOnlyWholeWords) {
        List<FoundObject> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidFile file : srcFiles) {
            if (file.getName().matches(regex)) {
                file.addFoundType(FoundObject.TYPE_FILE);
                found.add(file);
            }
        }
        return found;
    }

    /**
     * Поиск по именам меток.
     * @param query
     * @return
     */
/*    public static TreeMap<String, List<TetroidRecord>> searchInTags(
            Map<String, List<TetroidRecord>> tagsMap, String query, boolean isOnlyWholeWords) {
        TreeMap<String, List<TetroidRecord>> found = new TreeMap<>();
        String regex = buildRegex(query);
        for (Map.Entry<String, List<TetroidRecord>> tagEntry : tagsMap.entrySet()) {
            if (tagEntry.getKey().matches(regex)) {
                found.put(tagEntry.getKey(), tagEntry.getValue());
            }
        }
        return found;
    }*/

    public static TreeMap<String, TetroidTag> searchInTags(Map<String, TetroidTag> tagsMap, String query) {
        TreeMap<String, TetroidTag> found = new TreeMap<>();
        String regex = buildRegex(query);
        for (TetroidTag tagEntry : tagsMap.values()) {
            if (tagEntry.getName().matches(regex)) {
                found.put(tagEntry.getName(), tagEntry);
            }
        }
        return found;
    }

    public static List<FoundObject> searchInRecordTags(List<TetroidTag> tags, String query) {
        List<FoundObject> found = new ArrayList<>();
        String regex = buildRegex(query);
        for (TetroidTag tagEntry : tags) {
            if (tagEntry.getName().matches(regex)) {
                found.add(tagEntry);
            }
        }
        return found;
    }

    private static String buildRegex(String query) {
        return "(?is)" + ".*" + Pattern.quote(query) + ".*";
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setInText(boolean inText) {
        this.inText = inText;
    }

    public void setInRecordsNames(boolean inRecordsNames) {
        this.inRecordsNames = inRecordsNames;
    }

    public void setInAuthor(boolean inAuthor) {
        this.inAuthor = inAuthor;
    }

    public void setInUrl(boolean inUrl) {
        this.inUrl = inUrl;
    }

    public void setInTags(boolean inTags) {
        this.inTags = inTags;
    }

    public void setInNodes(boolean inNodes) {
        this.inNodes = inNodes;
    }

    public void setInFiles(boolean inFiles) {
        this.inFiles = inFiles;
    }

    public void setSplitToWords(boolean splitToWords) {
        isSplitToWords = splitToWords;
    }

    public void setOnlyWholeWords(boolean onlyWholeWords) {
        isOnlyWholeWords = onlyWholeWords;
    }

    public void setSearchInNode(boolean searchInNode) {
        isSearchInNode = searchInNode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(query);
        dest.writeInt((isSplitToWords) ? 1 : 0);
        dest.writeInt((isOnlyWholeWords) ? 1 : 0);
        dest.writeInt((isSearchInNode) ? 1 : 0);
    }

    public static final Creator<ScanManager> CREATOR = new Creator<ScanManager>() {
        @Override
        public ScanManager createFromParcel(Parcel in) {
            return new ScanManager(in);
        }

        @Override
        public ScanManager[] newArray(int size) {
            return new ScanManager[size];
        }
    };
}
