import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    private enum Kind {
        KEEP, DELETE, INSERT
    }

    private static final class Edit {
        final Kind kind;
        final int aIndex;
        final int bIndex;

        Edit(Kind kind, int aIndex, int bIndex) {
            this.kind = kind;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
        }
    }

    private static final class LineSequence implements MyersDiff.Sequence {
        private final byte[][] lines;

        LineSequence(byte[][] lines) {
            this.lines = lines;
        }

        @Override
        public int size() {
            return lines.length;
        }

        @Override
        public boolean equal(MyersDiff.Sequence other, int aIndex, int bIndex) {
            LineSequence that = (LineSequence) other;
            return Arrays.equals(lines[aIndex], that.lines[bIndex]);
        }
    }

    private static final class CodePointSequence implements MyersDiff.Sequence {
        private final int[] codePoints;

        CodePointSequence(int[] codePoints) {
            this.codePoints = codePoints;
        }

        @Override
        public int size() {
            return codePoints.length;
        }

        @Override
        public boolean equal(MyersDiff.Sequence other, int aIndex, int bIndex) {
            CodePointSequence that = (CodePointSequence) other;
            return codePoints[aIndex] == that.codePoints[bIndex];
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            return;
        }

        String command = args[0];
        String fileA = args[1];
        String fileB = args[2];

        try {
            byte[][] a = readLines(fileA);
            byte[][] b = readLines(fileB);

            if ("lines".equals(command)) {
                runLines(a, b);
            } else if ("highlight".equals(command)) {
                runHighlight(a, b);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Read the file exactly as bytes and split only on LF (0x0A).
     * CR, when present before LF, remains part of the line.
     */
    private static byte[][] readLines(String filename) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(filename));
        ArrayList<byte[]> lines = new ArrayList<>();

        int start = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(Arrays.copyOfRange(data, start, i));
                start = i + 1;
            }
        }

        // A final LF does not create an additional empty line.
        if (start < data.length) {
            lines.add(Arrays.copyOfRange(data, start, data.length));
        }

        return lines.toArray(new byte[0][]);
    }

    private static List<Edit> diffLines(byte[][] a, byte[][] b) {
        LineSequence left = new LineSequence(a);
        LineSequence right = new LineSequence(b);

        List<MyersDiff.Edit> raw = MyersDiff.diff(left, right);
        ArrayList<Edit> result = new ArrayList<>(raw.size());

        for (MyersDiff.Edit edit : raw) {
            switch (edit.type) {
                case ' ' -> result.add(new Edit(Kind.KEEP, edit.aIndex, edit.bIndex));
                case '-' -> result.add(new Edit(Kind.DELETE, edit.aIndex, -1));
                case '+' -> result.add(new Edit(Kind.INSERT, -1, edit.bIndex));
                default -> throw new IllegalStateException("Unknown edit type: " + edit.type);
            }
        }

        return result;
    }

    private static void runLines(byte[][] a, byte[][] b) throws IOException {
        List<Edit> edits = diffLines(a, b);
        BufferedOutputStream out = new BufferedOutputStream(System.out);

        int p = 0;
        while (p < edits.size()) {
            Edit edit = edits.get(p);

            if (edit.kind == Kind.KEEP) {
                writeLine(out, ' ', a[edit.aIndex]);
                p++;
                continue;
            }

            int q = p;
            while (q < edits.size() && edits.get(q).kind != Kind.KEEP) {
                q++;
            }

            // Required ordering: every deletion before every insertion.
            for (int k = p; k < q; k++) {
                Edit change = edits.get(k);
                if (change.kind == Kind.DELETE) {
                    writeLine(out, '-', a[change.aIndex]);
                }
            }

            for (int k = p; k < q; k++) {
                Edit change = edits.get(k);
                if (change.kind == Kind.INSERT) {
                    writeLine(out, '+', b[change.bIndex]);
                }
            }

            p = q;
        }

        out.flush();
    }

    private static void runHighlight(byte[][] a, byte[][] b) throws IOException {
        List<Edit> edits = diffLines(a, b);
        BufferedOutputStream out = new BufferedOutputStream(System.out);

        int p = 0;
        while (p < edits.size()) {
            Edit edit = edits.get(p);

            if (edit.kind == Kind.KEEP) {
                writeLine(out, ' ', a[edit.aIndex]);
                p++;
                continue;
            }

            int q = p;
            while (q < edits.size() && edits.get(q).kind != Kind.KEEP) {
                q++;
            }

            ArrayList<byte[]> deleted = new ArrayList<>();
            ArrayList<byte[]> inserted = new ArrayList<>();

            for (int k = p; k < q; k++) {
                Edit change = edits.get(k);
                if (change.kind == Kind.DELETE) {
                    deleted.add(a[change.aIndex]);
                }
            }

            for (int k = p; k < q; k++) {
                Edit change = edits.get(k);
                if (change.kind == Kind.INSERT) {
                    inserted.add(b[change.bIndex]);
                }
            }

            // All '-' lines must come before any '+' line.
            for (byte[] line : deleted) {
                writeLine(out, '-', line);
            }

            // After each paired '+' line, immediately print its '?' line.
            int pairs = Math.min(deleted.size(), inserted.size());
            for (int k = 0; k < inserted.size(); k++) {
                byte[] newLine = inserted.get(k);
                writeLine(out, '+', newLine);

                if (k < pairs) {
                    String oldText = new String(
                            deleted.get(k), StandardCharsets.UTF_8);
                    String newText = new String(
                            newLine, StandardCharsets.UTF_8);

                    String ranges = characterHighlight(oldText, newText);
                    out.write(ranges.getBytes(StandardCharsets.UTF_8));
                    out.write('\n');
                }
            }

            p = q;
        }

        out.flush();
    }

    private static void writeLine(
            BufferedOutputStream out,
            char prefix,
            byte[] line
    ) throws IOException {
        out.write(prefix);
        out.write(line);
        out.write('\n');
    }

    private static String characterHighlight(String oldLine, String newLine) {
        int[] oldPoints = oldLine.codePoints().toArray();
        int[] newPoints = newLine.codePoints().toArray();

        CodePointSequence oldSequence = new CodePointSequence(oldPoints);
        CodePointSequence newSequence = new CodePointSequence(newPoints);

        List<MyersDiff.Edit> edits = MyersDiff.diff(oldSequence, newSequence);

        boolean[] oldChanged = new boolean[oldPoints.length];
        boolean[] newChanged = new boolean[newPoints.length];

        for (MyersDiff.Edit edit : edits) {
            if (edit.type == '-') {
                oldChanged[edit.aIndex] = true;
            } else if (edit.type == '+') {
                newChanged[edit.bIndex] = true;
            }
        }

        return "? "
                + makeRanges(oldChanged)
                + " | "
                + makeRanges(newChanged);
    }

    private static String makeRanges(boolean[] changed) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        boolean first = true;

        while (i < changed.length) {
            if (!changed[i]) {
                i++;
                continue;
            }

            int start = i;
            while (i < changed.length && changed[i]) {
                i++;
            }
            int end = i;

            if (!first) {
                result.append(',');
            }
            result.append(start).append('-').append(end);
            first = false;
        }

        return first ? "." : result.toString();
    }
}
