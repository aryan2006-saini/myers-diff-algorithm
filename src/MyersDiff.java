import java.util.ArrayList;
import java.util.List;

final class MyersDiff {

    /*
     * Generic sequence interface.
     *
     * Part A:
     *     sequence = lines
     *
     * Part B:
     *     sequence = Unicode code points
     */
    interface Sequence {

        int size();

        boolean equal(
                Sequence other,
                int aIndex,
                int bIndex
        );
    }

    /*
     * One edit operation.
     *
     * ' ' = keep
     * '-' = delete
     * '+' = insert
     */
    static final class Edit {

        final char type;
        final int aIndex;
        final int bIndex;

        Edit(
                char type,
                int aIndex,
                int bIndex) {

            this.type = type;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
        }
    }

    /*
     * Represents a middle snake.
     */
    private static final class Snake {

        final int startA;
        final int endA;
        final int diagonal;

        Snake(
                int startA,
                int endA,
                int diagonal) {

            this.startA = startA;
            this.endA = endA;
            this.diagonal = diagonal;
        }
    }

    private final Sequence a;
    private final Sequence b;

    /*
     * Shared V arrays.
     *
     * They are allocated once for this diff.
     */
    private final int[] vDown;
    private final int[] vUp;

    private MyersDiff(
            Sequence a,
            Sequence b) {

        this.a = a;
        this.b = b;

        int size =
                a.size()
                        + b.size()
                        + 2;

        this.vDown =
                new int[size];

        this.vUp =
                new int[size];
    }

    /*
     * Public entry point.
     */
    static List<Edit> diff(
            Sequence a,
            Sequence b) {

        MyersDiff engine =
                new MyersDiff(a, b);

        List<Edit> result =
                new ArrayList<>();

        engine.buildScript(
                0,
                a.size(),
                0,
                b.size(),
                result
        );

        return result;
    }

    /*
     * Recursively reconstruct the shortest edit script.
     */
    private void buildScript(
            int startA,
            int endA,
            int startB,
            int endB,
            List<Edit> out) {

        /*
         * A is empty:
         * everything remaining in B is inserted.
         */
        if (startA == endA) {

            for (int j = startB;
                 j < endB;
                 j++) {

                out.add(
                        new Edit(
                                '+',
                                -1,
                                j
                        )
                );
            }

            return;
        }

        /*
         * B is empty:
         * everything remaining in A is deleted.
         */
        if (startB == endB) {

            for (int i = startA;
                 i < endA;
                 i++) {

                out.add(
                        new Edit(
                                '-',
                                i,
                                -1
                        )
                );
            }

            return;
        }

        /*
         * If the ranges are already identical,
         * no search is necessary.
         */
        if (allEqual(
                startA,
                endA,
                startB,
                endB)) {

            int length =
                    endA - startA;

            for (int i = 0;
                 i < length;
                 i++) {

                out.add(
                        new Edit(
                                ' ',
                                startA + i,
                                startB + i
                        )
                );
            }

            return;
        }

        /*
         * Find the middle snake of an optimal path.
         */
        Snake middle =
                getMiddleSnake(
                        startA,
                        endA,
                        startB,
                        endB
                );

        /*
         * If no meaningful snake exists,
         * solve this small subproblem directly.
         */
        if (middle == null
                || (
                    middle.startA == endA
                    && middle.diagonal
                    == endA - endB
                )
                || (
                    middle.endA == startA
                    && middle.diagonal
                    == startA - startB
                )) {

            directScript(
                    startA,
                    endA,
                    startB,
                    endB,
                    out
            );

            return;
        }

        int middleStartB =
                middle.startA
                        - middle.diagonal;

        int middleEndB =
                middle.endA
                        - middle.diagonal;

        /*
         * Left side.
         */
        buildScript(
                startA,
                middle.startA,
                startB,
                middleStartB,
                out
        );

        /*
         * Middle snake:
         * all elements are equal, therefore KEEP.
         */
        for (int i = middle.startA;
             i < middle.endA;
             i++) {

            int j =
                    i - middle.diagonal;

            out.add(
                    new Edit(
                            ' ',
                            i,
                            j
                    )
            );
        }

        /*
         * Right side.
         */
        buildScript(
                middle.endA,
                endA,
                middleEndB,
                endB,
                out
        );
    }

    /*
     * Direct solution for a small/degenerated region.
     *
     * It greedily keeps equal elements and otherwise
     * chooses delete/insert according to remaining
     * distance.
     *
     * The middle-snake routine handles normal regions;
     * this is only a terminal fallback.
     */
    private void directScript(
            int startA,
            int endA,
            int startB,
            int endB,
            List<Edit> out) {

        int i = startA;
        int j = startB;

        while (i < endA || j < endB) {

            /*
             * Equal elements are always free to keep.
             */
            if (i < endA
                    && j < endB
                    && a.equal(
                            b,
                            i,
                            j)) {

                out.add(
                        new Edit(
                                ' ',
                                i,
                                j
                        )
                );

                i++;
                j++;

                continue;
            }

            /*
             * Only insertions remain.
             */
            if (i >= endA) {

                out.add(
                        new Edit(
                                '+',
                                -1,
                                j
                        )
                );

                j++;

                continue;
            }

            /*
             * Only deletions remain.
             */
            if (j >= endB) {

                out.add(
                        new Edit(
                                '-',
                                i,
                                -1
                        )
                );

                i++;

                continue;
            }

            /*
             * Choose the side whose remaining
             * distance is larger.
             *
             * This preserves a deterministic result
             * for the terminal case.
             */
            int deleteRemaining =
                    endA - i;

            int insertRemaining =
                    endB - j;

            if (deleteRemaining
                    >= insertRemaining) {

                out.add(
                        new Edit(
                                '-',
                                i,
                                -1
                        )
                );

                i++;

            } else {

                out.add(
                        new Edit(
                                '+',
                                -1,
                                j
                        )
                );

                j++;
            }
        }
    }

    /*
     * Check whether two subranges are exactly equal.
     */
    private boolean allEqual(
            int startA,
            int endA,
            int startB,
            int endB) {

        int n =
                endA - startA;

        int m =
                endB - startB;

        if (n != m) {
            return false;
        }

        for (int i = 0;
             i < n;
             i++) {

            if (!a.equal(
                    b,
                    startA + i,
                    startB + i)) {

                return false;
            }
        }

        return true;
    }

    /*
     * Myers middle-snake search.
     *
     * This performs simultaneous forward and
     * backward searches until the two frontiers meet.
     */
    private Snake getMiddleSnake(
            int startA,
            int endA,
            int startB,
            int endB) {

        int n =
                endA - startA;

        int m =
                endB - startB;

        if (n == 0 || m == 0) {
            return null;
        }

        int delta =
                n - m;

        int total =
                n + m;

        /*
         * Enough space for every diagonal used
         * by this subproblem.
         */
        int offset =
                (total % 2 == 0
                        ? total
                        : total + 1) / 2;

        /*
         * Forward frontier starts before
         * the first element.
         */
        vDown[1 + offset] =
                startA;

        /*
         * Backward frontier starts after
         * the last element.
         */
        vUp[1 + offset] =
                endA + 1;

        for (int d = 0;
             d <= offset;
             d++) {

            /*
             * --------------------------------
             * FORWARD SEARCH
             * --------------------------------
             */
            for (int k = -d;
                 k <= d;
                 k += 2) {

                int index =
                        k + offset;

                /*
                 * Choose:
                 *
                 * insertion -> same x
                 * deletion  -> x + 1
                 */
                if (k == -d
                        || (
                            k != d
                            && vDown[index - 1]
                            < vDown[index + 1]
                        )) {

                    vDown[index] =
                            vDown[index + 1];

                } else {

                    vDown[index] =
                            vDown[index - 1] + 1;
                }

                int x =
                        vDown[index];

                int y =
                        x
                                - startA
                                + startB
                                - k;

                /*
                 * Follow the snake.
                 */
                while (
                        x < endA
                        && y < endB
                        && a.equal(
                                b,
                                x,
                                y)
                ) {

                    x++;
                    y++;

                    vDown[index] = x;
                }

                /*
                 * If delta is odd, the forward and
                 * backward searches can meet here.
                 */
                if ((delta & 1) != 0
                        && delta - d <= k
                        && k <= delta + d) {

                    if (vUp[index - delta]
                            <= vDown[index]) {

                        return buildSnake(
                                vUp[index - delta],
                                k + startA - startB,
                                endA,
                                endB
                        );
                    }
                }
            }

            /*
             * --------------------------------
             * BACKWARD SEARCH
             * --------------------------------
             */
            for (int k = delta - d;
                 k <= delta + d;
                 k += 2) {

                int index =
                        k
                                + offset
                                - delta;

                /*
                 * Choose the backward equivalent
                 * of insertion/deletion.
                 */
                if (
                        k == delta - d
                        || (
                            k != delta + d
                            && vUp[index + 1]
                            <= vUp[index - 1]
                        )
                ) {

                    vUp[index] =
                            vUp[index + 1] - 1;

                } else {

                    vUp[index] =
                            vUp[index - 1];
                }

                int x =
                        vUp[index] - 1;

                int y =
                        x
                                - startA
                                + startB
                                - k;

                /*
                 * Follow the backward snake.
                 */
                while (
                        x >= startA
                        && y >= startB
                        && a.equal(
                                b,
                                x,
                                y)
                ) {

                    vUp[index] = x;

                    x--;
                    y--;
                }

                /*
                 * If delta is even, the frontiers
                 * meet here.
                 */
                if ((delta & 1) == 0
                        && -d <= k
                        && k <= d) {

                    if (vUp[index]
                            <= vDown[index + delta]) {

                        return buildSnake(
                                vUp[index],
                                k + startA - startB,
                                endA,
                                endB
                        );
                    }
                }
            }
        }

        throw new IllegalStateException(
                "Myers middle snake not found"
        );
    }

    /*
     * Build the actual snake after the two
     * search frontiers meet.
     */
    private Snake buildSnake(
            int start,
            int diagonal,
            int endA,
            int endB) {

        int end = start;

        while (
                end < endA
                && end - diagonal < endB
                && a.equal(
                        b,
                        end,
                        end - diagonal)
        ) {

            end++;
        }

        return new Snake(
                start,
                end,
                diagonal
        );
    }
}

