package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 文件夹二级子项布局：与真机、应用预览共享同一套几何和动画顺序。 */
public final class FolderChildPacking {

    public static final int MODE_AUTO = 0;
    public static final int MODE_OUTER_ARC = 1;
    public static final int MODE_DOUBLE_ARC = 2;
    public static final int MODE_RING = 3;
    public static final int MODE_HONEYCOMB = 4;

    public static final int ORDER_GEOMETRY = 0;
    public static final int ORDER_LEFT_TO_RIGHT = 1;
    public static final int ORDER_RIGHT_TO_LEFT = 2;
    public static final int ORDER_OUTWARD = 3;
    public static final int ORDER_CENTER_OUT = 4;

    public static final class Result {
        public final List<PointF> points;
        public final List<Integer> animationOrder;
        public final int usedMode;
        public final float primaryRadius;

        Result(List<PointF> points, List<Integer> animationOrder, int usedMode, float primaryRadius) {
            this.points = points;
            this.animationOrder = animationOrder;
            this.usedMode = usedMode;
            this.primaryRadius = primaryRadius;
        }
    }

    private static final class Candidate {
        final int mode;
        final float radius;
        final List<PointF> points;
        final double score;

        Candidate(int mode, float radius, List<PointF> points, double score) {
            this.mode = mode;
            this.radius = radius;
            this.points = points;
            this.score = score;
        }
    }

    private FolderChildPacking() {}

    public static Result calculate(int count, float folderX, float folderY,
                                   float fodX, float fodY,
                                   float screenWidth, float screenHeight,
                                   float safeMargin, float iconSize,
                                   float minGap, float spreadScale,
                                   float arcSpanDeg, int directionMode,
                                   int orderMode, int requestedMode) {
        if (count <= 0) {
            return new Result(new ArrayList<>(), new ArrayList<>(), MODE_OUTER_ARC, 0f);
        }

        float spacing = iconSize + Math.max(0f, minGap);
        float baseAngle = resolveBaseAngle(folderX, folderY, fodX, fodY, directionMode);
        float spreadRadius = Math.max(iconSize * 1.05f, iconSize * spreadScale);

        List<Candidate> candidates = new ArrayList<>();
        candidates.add(buildOuterArc(count, folderX, folderY, screenWidth, screenHeight,
                safeMargin, iconSize, spacing, spreadRadius, arcSpanDeg, baseAngle));
        candidates.add(buildDoubleArc(count, folderX, folderY, screenWidth, screenHeight,
                safeMargin, iconSize, spacing, spreadRadius, arcSpanDeg, baseAngle));
        candidates.add(buildRing(count, folderX, folderY, screenWidth, screenHeight,
                safeMargin, iconSize, spacing, spreadRadius, baseAngle));
        candidates.add(buildHoneycomb(count, folderX, folderY, screenWidth, screenHeight,
                safeMargin, iconSize, spacing, spreadRadius, baseAngle));

        Candidate selected;
        if (requestedMode >= MODE_OUTER_ARC && requestedMode <= MODE_HONEYCOMB) {
            selected = candidates.get(requestedMode - MODE_OUTER_ARC);
        } else if (count <= 4) {
            selected = candidates.get(0);
        } else if (count <= 8) {
            selected = candidates.get(1).score <= candidates.get(2).score
                    ? candidates.get(1) : candidates.get(2);
        } else {
            selected = candidates.get(3);
        }

        List<Integer> order = buildOrder(selected.points, folderX, folderY, baseAngle, orderMode);
        return new Result(selected.points, order, selected.mode, selected.radius);
    }

    private static Candidate buildOuterArc(int count, float cx, float cy,
                                           float width, float height, float margin,
                                           float iconSize, float spacing,
                                           float requestedRadius, float spanDeg,
                                           float baseAngle) {
        float radius = requestedRadius;
        if (count > 1 && spanDeg > 1f) {
            float step = (float) Math.toRadians(spanDeg / (count - 1));
            radius = Math.max(radius, spacing / (2f * (float) Math.sin(step / 2f)));
        }
        List<PointF> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int offsetDeg = -60; offsetDeg <= 60; offsetDeg += 5) {
            List<PointF> points = arcPoints(count, cx, cy, radius, spanDeg,
                    baseAngle + (float) Math.toRadians(offsetDeg));
            double score = boundaryScore(points, cx, cy, width, height, margin, iconSize);
            if (score < bestScore) {
                bestScore = score;
                best = points;
            }
        }
        return new Candidate(MODE_OUTER_ARC, radius, best, bestScore);
    }

    private static Candidate buildDoubleArc(int count, float cx, float cy,
                                            float width, float height, float margin,
                                            float iconSize, float spacing,
                                            float requestedRadius, float spanDeg,
                                            float baseAngle) {
        int innerCount = (count + 1) / 2;
        int outerCount = count - innerCount;
        float innerRadius = requestedRadius;
        float outerRadius = requestedRadius * 1.55f;
        if (innerCount > 1 && spanDeg > 1f) {
            float step = (float) Math.toRadians(spanDeg / (innerCount - 1));
            innerRadius = Math.max(innerRadius, spacing / (2f * (float) Math.sin(step / 2f)));
        }
        if (outerCount > 1 && spanDeg > 1f) {
            float step = (float) Math.toRadians(spanDeg / (outerCount - 1));
            outerRadius = Math.max(outerRadius, spacing / (2f * (float) Math.sin(step / 2f)));
        }

        List<PointF> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int offsetDeg = -50; offsetDeg <= 50; offsetDeg += 5) {
            float angle = baseAngle + (float) Math.toRadians(offsetDeg);
            List<PointF> points = new ArrayList<>(count);
            points.addAll(arcPoints(innerCount, cx, cy, innerRadius, spanDeg, angle));
            if (outerCount > 0) {
                points.addAll(arcPoints(outerCount, cx, cy, outerRadius, spanDeg, angle));
            }
            double score = boundaryScore(points, cx, cy, width, height, margin, iconSize);
            if (score < bestScore) {
                bestScore = score;
                best = points;
            }
        }
        return new Candidate(MODE_DOUBLE_ARC, innerRadius, best, bestScore);
    }

    private static Candidate buildRing(int count, float cx, float cy,
                                       float width, float height, float margin,
                                       float iconSize, float spacing,
                                       float requestedRadius, float baseAngle) {
        float minRadius = count == 1
                ? requestedRadius
                : spacing / (2f * (float) Math.sin(Math.PI / count));
        float radius = Math.max(requestedRadius, minRadius);
        List<PointF> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int offsetDeg = -90; offsetDeg <= 90; offsetDeg += 5) {
            float startAngle = baseAngle + (float) Math.toRadians(offsetDeg);
            List<PointF> points = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double angle = startAngle + i * 2d * Math.PI / count;
                points.add(pointAt(cx, cy, radius, angle));
            }
            double score = boundaryScore(points, cx, cy, width, height, margin, iconSize);
            if (score < bestScore) {
                bestScore = score;
                best = points;
            }
        }
        return new Candidate(MODE_RING, radius, best, bestScore);
    }

    private static Candidate buildHoneycomb(int count, float cx, float cy,
                                            float width, float height, float margin,
                                            float iconSize, float spacing,
                                            float requestedRadius, float baseAngle) {
        List<PointF> candidates = new ArrayList<>();
        float ringSpacing = Math.max(requestedRadius * 0.72f, spacing * 0.92f);
        candidates.add(pointAt(cx, cy, requestedRadius, baseAngle));
        for (int ring = 1; candidates.size() < count && ring <= 5; ring++) {
            float radius = requestedRadius + ringSpacing * ring;
            int ringItemCount = Math.min(6 * ring, count - candidates.size());
            float start = baseAngle + (ring % 2 == 0 ? (float) Math.PI / ringItemCount : 0f);
            for (int i = 0; i < ringItemCount; i++) {
                double angle = start + i * 2d * Math.PI / ringItemCount;
                candidates.add(pointAt(cx, cy, radius, angle));
            }
        }
        if (candidates.size() > count) candidates = new ArrayList<>(candidates.subList(0, count));
        double score = boundaryScore(candidates, cx, cy, width, height, margin, iconSize);
        return new Candidate(MODE_HONEYCOMB, requestedRadius, candidates, score);
    }

    private static List<PointF> arcPoints(int count, float cx, float cy,
                                          float radius, float spanDeg, float centerAngle) {
        List<PointF> points = new ArrayList<>(count);
        if (count <= 0) return points;
        double start = count == 1
                ? centerAngle
                : centerAngle - Math.toRadians(spanDeg) / 2d;
        double step = count == 1 ? 0d : Math.toRadians(spanDeg) / (count - 1);
        for (int i = 0; i < count; i++) {
            points.add(pointAt(cx, cy, radius, start + i * step));
        }
        return points;
    }

    private static PointF pointAt(float cx, float cy, float radius, double angle) {
        return new PointF(
                cx + radius * (float) Math.cos(angle),
                cy + radius * (float) Math.sin(angle)
        );
    }

    private static float resolveBaseAngle(float folderX, float folderY,
                                          float fodX, float fodY, int directionMode) {
        float outward = (float) Math.atan2(folderY - fodY, folderX - fodX);
        switch (directionMode) {
            case 1:
                return outward;
            case 2:
                return outward + (float) Math.PI;
            case 3:
                return (float) (-Math.PI / 2d);
            default:
                return outward;
        }
    }

    private static double boundaryScore(List<PointF> points, float folderX, float folderY,
                                        float width, float height, float margin,
                                        float iconSize) {
        double score = 0d;
        float half = iconSize / 2f;
        float minX = margin + half;
        float maxX = width - margin - half;
        float minY = margin + half;
        float maxY = height - margin - half;
        for (PointF p : points) {
            if (p.x < minX) score += (minX - p.x) * (minX - p.x);
            if (p.x > maxX) score += (p.x - maxX) * (p.x - maxX);
            if (p.y < minY) score += (minY - p.y) * (minY - p.y);
            if (p.y > maxY) score += (p.y - maxY) * (p.y - maxY);
            float distanceFromFolder = (float) Math.hypot(p.x - folderX, p.y - folderY);
            if (distanceFromFolder < iconSize * 1.02f) {
                score += Math.pow(iconSize * 1.02f - distanceFromFolder, 2d) * 4d;
            }
        }
        return score;
    }

    private static List<Integer> buildOrder(List<PointF> points, float folderX, float folderY,
                                            float baseAngle, int orderMode) {
        List<Integer> order = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) order.add(i);
        switch (orderMode) {
            case ORDER_LEFT_TO_RIGHT:
                order.sort(Comparator.comparingDouble(i -> points.get(i).x));
                break;
            case ORDER_RIGHT_TO_LEFT:
                order.sort((a, b) -> Float.compare(points.get(b).x, points.get(a).x));
                break;
            case ORDER_OUTWARD:
                order.sort(Comparator.comparingDouble(i -> {
                    PointF p = points.get(i);
                    return Math.hypot(p.x - folderX, p.y - folderY);
                }));
                break;
            case ORDER_CENTER_OUT:
                order.sort(Comparator.comparingDouble(i -> {
                    PointF p = points.get(i);
                    double angle = Math.atan2(p.y - folderY, p.x - folderX) - baseAngle;
                    return Math.abs(Math.atan2(Math.sin(angle), Math.cos(angle)));
                }));
                break;
            case ORDER_GEOMETRY:
            default:
                break;
        }
        return order;
    }
}
