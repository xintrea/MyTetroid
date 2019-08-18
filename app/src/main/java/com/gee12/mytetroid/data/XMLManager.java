package com.gee12.mytetroid.data;

import android.util.Xml;

import com.gee12.mytetroid.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

/**
 *
 */
public abstract class XMLManager implements INodeIconLoader, ITagsParseHandler {

    private Comparator<String> tagsComparator = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            if (o1 == o2) {
                return 0;
            }
            if (o1 == null) {
                return -1;
            }
            if (o2 == null) {
                return 1;
            }
            return o1.toLowerCase().compareTo(o2.toLowerCase());
        }
    };

    private static final String ns = null;
    private static final String TAGS_SEPARATOR = ", ";

    protected Version formatVersion;
    protected boolean isExistCryptedNodes;  // а вообще можно читать из crypt_mode=1

    protected IDecryptHandler decryptCallback;

    /**
     *
     */
    protected List<TetroidNode> rootNodesList;

    /**
     *
     */
    private TreeMap<String,TetroidTag> tagsMap;
    protected List<TetroidTag> tagsList;

    /**
     *
     * @param in Файл mytetra.xml
     * @return Иерархический список веток с записями и документами
     * @throws XmlPullParserException
     * @throws IOException
     */
    public boolean parse(InputStream in, IDecryptHandler decryptHandler) throws XmlPullParserException, IOException {
        this.decryptCallback = decryptHandler;
        this.rootNodesList = new ArrayList<>();
        this.tagsMap = new TreeMap<>(tagsComparator);
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readRoot(parser);
        } finally {
            in.close();
            this.tagsList = new ArrayList<>(tagsMap.values());
//            this.tagsMap = null;
        }
    }

    private boolean readRoot(XmlPullParser parser) throws XmlPullParserException, IOException {
        boolean res = false;
        parser.require(XmlPullParser.START_TAG, ns, "root");
        while (parser.next() != XmlPullParser.END_TAG) {
//        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("format")) {
                formatVersion = readFormatVersion(parser);
            } else if (tagName.equals("content")) {
                res = readContent(parser);
            }
        }
        return res;
    }

    private Version readFormatVersion(XmlPullParser parser) throws XmlPullParserException, IOException {
        int version = 0;
        int subversion = 0;
        parser.require(XmlPullParser.START_TAG, ns, "format");
        String tagName = parser.getName();
        if (tagName.equals("format")) {
            version = Integer.parseInt(parser.getAttributeValue(ns, "version"));
            subversion = Integer.parseInt(parser.getAttributeValue(ns, "subversion"));
        }
        // принудительно вызываем nextTag(), чтобы найти закрытие тега "/>"
        parser.nextTag();
        parser.require(XmlPullParser.END_TAG, ns, "format");
        return new Version(version, subversion);
    }

    private boolean readContent(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<TetroidNode> nodes = new ArrayList();
        parser.require(XmlPullParser.START_TAG, ns, "content");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("node")) {
                nodes.add(readNode(parser, 0));
            } else {
                skip(parser);
            }
        }
        this.rootNodesList = nodes;
        return true;
    }

    private TetroidNode readNode(XmlPullParser parser, int level) throws XmlPullParserException, IOException {
        boolean crypt = false;
        String id = null;
        String name = null;
        String iconPath = null; // например: "/Gnome/color_gnome_2_computer.svg"
        parser.require(XmlPullParser.START_TAG, ns, "node");
        String tagName = parser.getName();
        if (tagName.equals("node")) {
            crypt = ("1".equals(parser.getAttributeValue(ns, "crypt")));
            // наличие зашифрованных веток
            if (crypt && !isExistCryptedNodes)
                isExistCryptedNodes = true;
            id = parser.getAttributeValue(ns, "id");
            name = parser.getAttributeValue(ns, "name");
            iconPath = parser.getAttributeValue(ns, "icon");
        }
        List<TetroidNode> subNodes = new ArrayList<>();
        List<TetroidRecord> records = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            tagName = parser.getName();
            if (tagName.equals("recordtable")) {
                // записи
                records = readRecords(parser);
            } else if (tagName.equals("node")) {
                // вложенная ветка
                subNodes.add(readNode(parser, level+1));
            } else {
                skip(parser);
            }
        }
        TetroidNode node = new TetroidNode(crypt, id, name, iconPath, subNodes, records, level);

        // расшифровка
        if (crypt && decryptCallback != null) {
            decryptCallback.decryptNode(node);
        }
        //else if (!crypt) {
        if (node.isNonCryptedOrDecrypted()) {
            // парсим метки
            parseNodeTags(node);
        }

        // загрузка иконки из файла (после расшифровки имени иконки)
        loadIcon(node);

        return node;
    }

    private List<TetroidRecord> readRecords(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<TetroidRecord> records = new ArrayList<>();
        parser.require(XmlPullParser.START_TAG, ns, "recordtable");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("record")) {
                TetroidRecord record = readRecord(parser);
                records.add(record);
            } else {
                skip(parser);
            }
        }
        return records;
    }

    protected void parseNodeTags(TetroidNode node) {
        for(TetroidRecord record : node.getRecords()) {
            parseTags(record);
        }
    }

    @Override
    public void parseRecordTags(TetroidRecord record) {
        parseTags(record);
    }

    /*
    protected void parseTags(TetroidRecord record) {
//        if (!record.isNonCryptedOrDecrypted())
//            return;
        String tagsString = record.getTagsString();
        if (!Utils.isNullOrEmpty(tagsString)) {
            for (String tag : tagsString.split(TAGS_SEPARATOR)) {
                if (tagsMap.containsKey(tag)) {
                    tagsMap.get(tag).add(record);
                } else {
                    List<TetroidRecord> tagRecords = new ArrayList<>();
                    tagRecords.add(record);
                    tagsMap.put(tag, tagRecords);
                }
            }
        }
    }*/

    protected void parseTags(TetroidRecord record) {
//        if (!record.isNonCryptedOrDecrypted())
//            return;
        String tagsString = record.getTagsString();
        if (!Utils.isNullOrEmpty(tagsString)) {
            for (String tag : tagsString.split(TAGS_SEPARATOR)) {
                if (tagsMap.containsKey(tag)) {
                    tagsMap.get(tag).addRecord(record);
                } else {
                    List<TetroidRecord> tagRecords = new ArrayList<>();
                    tagRecords.add(record);
                    tagsMap.put(tag, new TetroidTag(tag, tagRecords));
                }
            }
        }
    }

    private TetroidRecord readRecord(XmlPullParser parser) throws XmlPullParserException, IOException {
        boolean crypt = false;
        String id = null;
        String name = null;
        String tags = null;
        String author = null;
        String url = null;
        Date created = null;
        String dirName = null;
        String fileName = null;
        parser.require(XmlPullParser.START_TAG, ns, "record");
        String tagName = parser.getName();
        if (tagName.equals("record")) {
            crypt = ("1".equals(parser.getAttributeValue(ns, "crypt")));
            id = parser.getAttributeValue(ns, "id");
            name = parser.getAttributeValue(ns, "name");
            tags = parser.getAttributeValue(ns, "tags");
            author = parser.getAttributeValue(ns, "author");
            url = parser.getAttributeValue(ns, "url");
            // строка вида "yyyyMMddHHmmss" (например, "20180901211132")
            created = Utils.toDate(parser.getAttributeValue(ns, "ctime"), "yyyyMMddHHmmss");
            dirName = parser.getAttributeValue(ns, "dir");
            fileName = parser.getAttributeValue(ns, "file");
        }
        TetroidRecord record = new TetroidRecord(crypt, id, name, tags, author, url, created, dirName, fileName);
//        parser.nextTag();
//        parser.require(XmlPullParser.END_TAG, ns, "record");
        // файлы
        List<TetroidFile> files = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            tagName = parser.getName();
            if (tagName.equals("files")) {
                files = readFiles(parser, record);
            } else {
                skip(parser);
            }
        }
        if (files != null) {
            record.setFiles(files);
        }
        parser.require(XmlPullParser.END_TAG, ns, "record");
        return record;
    }

    private List<TetroidFile> readFiles(XmlPullParser parser, TetroidRecord record) throws XmlPullParserException, IOException {
        List<TetroidFile> files = new ArrayList<>();
        parser.require(XmlPullParser.START_TAG, ns, "files");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("file")) {
                files.add(readFile(parser, record));
            } else {
                skip(parser);
            }
        }
        return files;
    }

    private TetroidFile readFile(XmlPullParser parser, TetroidRecord record) throws XmlPullParserException, IOException {
        String id = null;
        String fileName = null;
        String type = null;
        parser.require(XmlPullParser.START_TAG, ns, "file");
        String tagName = parser.getName();
        if (tagName.equals("file")) {
            id = parser.getAttributeValue(ns, "id");
            fileName = parser.getAttributeValue(ns, "fileName");
            type = parser.getAttributeValue(ns, "type");
        }
        // принудительно вызываем nextTag(), чтобы найти закрытие тега "/>"
        parser.nextTag();
        parser.require(XmlPullParser.END_TAG, ns, "file");
        return new TetroidFile(id, fileName, type, record);
    }

    /**
     * This is how it works:
     * - It throws an exception if the current event isn't a START_TAG.
     * - It consumes the START_TAG, and all events up to and including the matching END_TAG.
     * - To make sure that it stops at the correct END_TAG and not at the first tag it encounters after the original START_TAG, it keeps track of the nesting depth.
     * @param parser
     * @throws XmlPullParserException
     * @throws IOException
     */
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

}
