package se.linuxgods.kreiger;

import org.apache.commons.lang.StringUtils;

public class Similarity {
    private String s1;
    private String s2;
    private int distance;

    public Similarity(String s1, String s2, int distance) {
        this.s1 = s1;
        this.s2 = s2;
        this.distance = distance;
    }

    public static Similarity levenshtein(String s1, String s2) {
        int distance = StringUtils.getLevenshteinDistance(s1, s2);
        return new Similarity(s1, s2, distance);
    }

    public float toLongest() {
        int maxLength = getLongestLength();
        int similarity = maxLength - distance;
        return ((float)similarity) / maxLength ;
    }

    private int getLongestLength() {
        return Math.max(s1.length(), s2.length());
    }

    @Override
    public String toString() {
        int maxLength = getLongestLength();
        int similarity = maxLength - distance;
        return "\""+s1+"\"-\""+s2+"\": "+similarity+"/"+maxLength;
    }
}
