package net.sf.jabref.model.groups;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.sf.jabref.logic.util.MetadataSerializationConfiguration;
import net.sf.jabref.model.FieldChange;
import net.sf.jabref.model.entry.BibEntry;
import net.sf.jabref.model.entry.KeywordList;
import net.sf.jabref.model.strings.StringUtil;

/**
 * Matches entries if a given field contains a specified word.
 */
public class SimpleKeywordGroup extends KeywordGroup implements GroupEntryChanger {

    private final List<String> searchWords;
    private final Character keywordSeparator;

    public SimpleKeywordGroup(String name, GroupHierarchyType context, String searchField,
                              String searchExpression, boolean caseSensitive, Character keywordSeparator) {
        super(name, context, searchField, searchExpression, caseSensitive);

        this.keywordSeparator = keywordSeparator;
        this.searchWords = StringUtil.getStringAsWords(searchExpression);
    }



    /**
     * Returns a String representation of this object that can be used to
     * reconstruct it.
     */
    @Override
    public String toString() {
        return MetadataSerializationConfiguration.KEYWORD_GROUP_ID + StringUtil.quote(getName(), MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR) +
                MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR
                + getContext().ordinal() + MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR
                + StringUtil.quote(searchField, MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR) + MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR
                + StringUtil.quote(searchExpression, MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR, MetadataSerializationConfiguration.GROUP_QUOTE_CHAR)
                + MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR + StringUtil.booleanToBinaryString(caseSensitive) + MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR
                + StringUtil.booleanToBinaryString(regExp) + MetadataSerializationConfiguration.GROUP_UNIT_SEPARATOR;
    }

    @Override
    public boolean supportsAdd() {
        return !regExp;
    }

    @Override
    public boolean supportsRemove() {
        return !regExp;
    }

    @Override
    public Optional<EntriesGroupChange> add(List<BibEntry> entriesToAdd) {
        if (!supportsAdd()) {
            return Optional.empty();
        }
        if ((entriesToAdd != null) && !(entriesToAdd.isEmpty())) {
            List<FieldChange> changes = new ArrayList<>();
            boolean modified = false;
            for (BibEntry entry : entriesToAdd) {
                if (!contains(entry)) {
                    String oldContent = entry.getField(searchField).orElse(null);
                    KeywordList wordlist = KeywordList.parse(oldContent, keywordSeparator);
                    wordlist.add(searchExpression);
                    String newContent = wordlist.getAsString(keywordSeparator);
                    entry.setField(searchField, newContent);

                    // Store change information.
                    changes.add(new FieldChange(entry, searchField, oldContent, newContent));
                    modified = true;
                }
            }

            return modified ? Optional.of(new EntriesGroupChange(changes)) : Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Optional<EntriesGroupChange> remove(List<BibEntry> entriesToRemove) {
        if (!supportsRemove()) {
            return Optional.empty();
        }

        if ((entriesToRemove != null) && (!entriesToRemove.isEmpty())) {
            List<FieldChange> changes = new ArrayList<>();
            boolean modified = false;
            for (BibEntry entry : entriesToRemove) {
                if (contains(entry)) {
                    String oldContent = entry.getField(searchField).orElse(null);
                    removeMatches(entry);

                    // Store change information.
                    changes.add(new FieldChange(entry, searchField, oldContent,
                            entry.getField(searchField).orElse(null)));
                    modified = true;
                }
            }

            return modified ? Optional.of(new EntriesGroupChange(changes)) : Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimpleKeywordGroup)) {
            return false;
        }
        SimpleKeywordGroup other = (SimpleKeywordGroup) o;
        return getName().equals(other.getName())
                && searchField.equals(other.searchField)
                && searchExpression.equals(other.searchExpression)
                && (caseSensitive == other.caseSensitive)
                && (getHierarchicalContext() == other.getHierarchicalContext());
    }

    @Override
    public boolean contains(BibEntry entry) {
        Set<String> words = entry.getFieldAsWords(searchField);
        if (words.isEmpty()) {
            return false;
        }

        if (caseSensitive) {
            return words.containsAll(searchWords);
        }
        return containsCaseInsensitive(searchWords, words);
    }

    private boolean containsCaseInsensitive(List<String> searchText, Set<String> words) {
        for (String searchWord : searchText) {
            if (!containsCaseInsensitive(searchWord, words)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsCaseInsensitive(String text, Set<String> words) {
        for (String word : words) {
            if (word.equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Look for the given non-regexp string in another string, but check whether a
     * match concerns a complete word, not part of a word.
     *
     * @param word The word to look for.
     * @param text The string to look in.
     * @return true if the word was found, false otherwise.
     */
    public static boolean containsWord(String word, String text) {
        int piv = 0;
        while (piv < text.length()) {
            int index = text.indexOf(word, piv);
            if (index < 0) {
                return false;
            }
            // Found a match. See if it is a complete word:
            if (((index == 0) || !Character.isLetterOrDigit(text.charAt(index - 1))) &&
                    (((index + word.length()) == text.length())
                            || !Character.isLetterOrDigit(text.charAt(index + word.length())))) {
                return true;
            } else {
                piv = index + 1;
            }
        }
        return false;
    }

    /**
     * Removes matches of searchString in the entry's field. This is only
     * possible if the search expression is not a regExp.
     */
    private void removeMatches(BibEntry entry) {
        entry.getField(searchField).ifPresent(content -> {
            StringBuffer sbOrig = new StringBuffer(content);
            StringBuffer sbLower = new StringBuffer(content.toLowerCase());
            StringBuffer haystack = caseSensitive ? sbOrig : sbLower;
            String needle = caseSensitive ? searchExpression : searchExpression.toLowerCase();
            int i;
            int j;
            int k;
            while ((i = haystack.indexOf(needle)) >= 0) {
                sbOrig.replace(i, i + needle.length(), "");
                sbLower.replace(i, i + needle.length(), "");
                // reduce spaces at i to 1
                j = i;
                k = i;
                while (((j - 1) >= 0) && (keywordSeparator.toString().indexOf(haystack.charAt(j - 1)) >= 0)) {
                    --j;
                }
                while ((k < haystack.length()) && (keywordSeparator.toString().indexOf(haystack.charAt(k)) >= 0)) {
                    ++k;
                }
                sbOrig.replace(j, k, (j >= 0) && (k < sbOrig.length()) ? keywordSeparator.toString() : "");
                sbLower.replace(j, k, (j >= 0) && (k < sbOrig.length()) ? keywordSeparator.toString() : "");
            }

            String result = sbOrig.toString().trim();
            if (result.isEmpty()) {
                entry.clearField(searchField);
            } else {
                entry.setField(searchField, result);
            }
        });
    }

    @Override
    public AbstractGroup deepCopy() {
        return new SimpleKeywordGroup(getName(), getContext(), searchField, searchExpression,
                caseSensitive, keywordSeparator);
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }
}
