package com.devn1n.downly;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// IDM-style categories. The file name decides the folder; torrents stay together in one folder.
final class Categories {
    static final String VIDEO = "Video", MUSIC = "Music", DOCUMENTS = "Documents", COMPRESSED = "Compressed",
        PROGRAMS = "Programs", IMAGES = "Images", TORRENTS = "Torrents", OTHER = "Other";
    static final String[] ALL = {VIDEO, MUSIC, DOCUMENTS, COMPRESSED, PROGRAMS, IMAGES, TORRENTS, OTHER};

    private static Set<String> set(String s) { return new HashSet<>(Arrays.asList(s.split(" "))); }
    private static final Set<String> VID = set("mp4 mkv avi mov webm m4v 3gp flv wmv ts mpeg mpg");
    private static final Set<String> MUS = set("mp3 m4a aac flac wav ogg opus wma amr mid");
    private static final Set<String> DOC = set("pdf doc docx txt epub xls xlsx ppt pptx csv rtf odt md mobi json xml");
    private static final Set<String> ARC = set("zip rar 7z tar gz xz bz2 tgz zst");
    private static final Set<String> APP = set("apk xapk apks exe msi deb rpm dmg iso img bin appimage");
    private static final Set<String> IMG = set("jpg jpeg png gif webp svg heic heif bmp tif tiff avif");

    static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String ofFile(String name) {
        String e = ext(name);
        if (VID.contains(e)) return VIDEO;
        if (MUS.contains(e)) return MUSIC;
        if (DOC.contains(e)) return DOCUMENTS;
        if (ARC.contains(e)) return COMPRESSED;
        if (APP.contains(e)) return PROGRAMS;
        if (IMG.contains(e)) return IMAGES;
        if (e.equals("torrent")) return TORRENTS;
        return OTHER;
    }

    // record type: http | magnet | torrent | video
    static String of(String recordType, String fileName) {
        if (recordType.equals("magnet") || recordType.equals("torrent")) return TORRENTS;
        return ofFile(fileName);
    }
}
