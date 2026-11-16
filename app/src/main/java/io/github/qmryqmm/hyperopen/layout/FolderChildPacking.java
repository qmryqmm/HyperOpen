package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 文件夹二级子项的固定圆环布局，真机与应用预览共享几何和动画顺序。 */
public final class FolderChildPacking {

    public static final int DIRECTION_AUTO = 0;
    public static final int DIRECTION_OUTWARD = 1;
    public static final int DIRECTION_INWARD = 2;
    public static final int DIRECTION_UP = 3;
    public static final int DIRECTION_DOWN = 4;
    public static final int DIRECTION_LEFT = 5;
    public static final int DIRECTION_RIGHT = 6;
    public static final int DIRECTION_CUSTOM = 7;

    public static final int ORDER_GEOMETRY = 0;
    public static final int ORDER_LEFT_TO_RIGHT = 1;
    public static final int ORDER_RIGHT_TO_LEFT = 2;
    public static final int ORDER_OUTWARD = 3;
    public static final int ORDER_CENTER_OUT = 4;

    public static final class Result {
        public final List<PointF> points;
        public final List<Integer> animationOrder;
        public final float radius;
        public final float directionDeg;

        Result(List<PointF> points, List<Integer> animationOrder,
               float radius, float directionDeg) {
            this.points = points;
            this.animationOrder = animationOrder;
            this.radius = radius;
            this.directionDeg = directionDeg;
        }
    }

    private static final class ScoreContext {
        final float folderX;
        final float folderY;
        final float fodX;
        final float fodY;
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;
        final float iconSize;
        final float spacing;

        ScoreContext(float folderX, float folderY, float fodX, float fodY,
                     float minX, float maxX, float minY, float maxY,
                     float iconSize, float spacing) {
            this.folderX = folderX;
            this.folderY = folderY;
            this.fodX = fodX;
            this.fodY = fodY;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.iconSize = iconSize;
            this.spacing = spacing;
        }
    }

    private static final class Candidate {
        final List<PointF> points;
        final float radius;
        final float directionDeg;
        final double score;

        Candidate(List<PointF> points, float radius, float directionDeg, double score) {
            this.points = points;
            this.radius = radius;
            this.directionDeg = directionDeg;
            this.score = score;
        }
    }

    private FolderChildPacking() {}

    public static Result calculate(int count,
                                   float folderX, float folderY,
                                   float fodX, float fodY,
                                   float safeLeft, float safeTop,
                                   float safeRight, float safeBottom,
                                   float iconSize, float minGap,
                                   float displayRangeScale,
                                   int directionMode, float directionOffsetDeg,
                                   int orderMode) {
        if (count <= 0) {
            return new Result(new ArrayList<>(), new ArrayList<>(), 0f, 0f);
        }

        float spacing = iconSize + Math.max(0f, minGap);
        float halfIconSize = iconSize / 2f;
        float minX = safeLeft + halfIconSize;
        float maxX = safeRight - halfIconSize;
        float minY = safeTop + halfIconSize;
        float maxY = safeBottom - halfIconSize;
        if (maxX < minX) {
            float centerX = (safeLeft + safeRight) / 2f;
            minX = centerX;
            maxX = centerX;
        }
        if (maxY < minY) {
            float centerY = (safeTop + safeBottom) / 2f;
            minY = centerY;
            maxY = centerY;
        }

        ScoreContext scoreContext = new ScoreContext(
                folderX, folderY, fodX, fodY,
                minX, maxX, minY, maxY,
                iconSize, spacing
        );
        float outwardDeg = (float) Math.toDegrees(Math.atan2(folderY - fodY, folderX - fodX));
        float preferredDeg = resolveDirectionDeg(
                outwardDeg, directionMode, directionOffsetDeg
        );
        List<Float> candidateAngles = directionMode == DIRECTION_AUTO
                ? buildAutoAngles()
                : Collections.singletonList(preferredDeg);
        float rangeScale = clamp(displayRangeScale, 0.5f, 2.5f);
        float angleStepDeg = count == 1
                ? 0f
                : Math.min(36f, 300f / (count - 1));
        float minimumRadius = count == 1
                ? iconSize * 1.25f
                : spacing / (2f * (float) Math.sin(Math.toRadians(angleStepDeg) / 2d));
        float radius = Math.max(
                iconSize * 1.35f * rangeScale,
                minimumRadius
        );

        Candidate selected = null;
        for (float directionDeg : candidateAngles) {
            Candidate candidate = buildRing(
                    count, folderX, folderY, radius, angleStepDeg,
                    directionDeg, scoreContext
            );
            if (selected == null || candidate.score < selected.score) {
                selected = candidate;
            }
        }

        List<PointF> fittedPoints = clampPointsToSafeBounds(selected.points, scoreContext);
        List<Integer> order = buildOrder(
                fittedPoints, folderX, folderY, selected.directionDeg, orderMode
        );
        return new Result(
                fittedPoints, order, selected.radius,
                normalizeDeg(selected.directionDeg)
        );
    }

    private static Candidate buildRing(int count, float folderX, float folderY,
                                       float radius, float angleStepDeg,
                                       float directionDeg,
                                       ScoreContext scoreContext) {
        List<PointF> points = new ArrayList<>(count);
        double centerIndex = (count - 1) / 2d;
        double directionRad = Math.toRadians(directionDeg);
        double stepRad = Math.toRadians(angleStepDeg);
        for (int index = 0; index < count; index++) {
            double angle = directionRad + (index - centerIndex) * stepRad;
            points.add(pointAt(folderX, folderY, radius, angle));
        }
        double score = boundaryScore(points, scoreContext)
                + collisionScore(points, scoreContext)
                + fodScore(points, scoreContext);
        return new Candidate(points, radius, directionDeg, score);
    }

    private static double boundaryScore(List<PointF> points, ScoreContext scoreContext) {
        double score = 0d;
        for (PointF point : points) {
            if (point.x < scoreContext.minX) {
                double overflow = scoreContext.minX - point.x;
                score += overflow * overflow * 3d;
            }
            if (point.x > scoreContext.maxX) {
                double overflow = point.x - scoreContext.maxX;
                score += overflow * overflow * 3d;
            }
            if (point.y < scoreContext.minY) {
                double overflow = scoreContext.minY - point.y;
                score += overflow * overflow * 3d;
            }
            if (point.y > scoreContext.maxY) {
                double overflow = point.y - scoreContext.maxY;
                score += overflow * overflow * 3d;
            }
        }
        return score;
    }

    private static double collisionScore(List<PointF> points, ScoreContext scoreContext) {
        double score = 0d;
        float folderClearance = scoreContext.iconSize * 1.04f;
        float childClearance = scoreContext.spacing * 0.92f;
        for (PointF point : points) {
            float folderDistance = (float) Math.hypot(
                    point.x - scoreContext.folderX, point.y - scoreContext.folderY
            );
            if (folderDistance < folderClearance) {
                double overlap = folderClearance - folderDistance;
                score += overlap * overlap * 5d;
            }
        }
        for (int firstIndex = 0; firstIndex < points.size(); firstIndex++) {
            PointF first = points.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < points.size(); secondIndex++) {
                PointF second = points.get(secondIndex);
                float distance = (float) Math.hypot(first.x - second.x, first.y - second.y);
                if (distance < childClearance) {
                    double overlap = childClearance - distance;
                    score += overlap * overlap * 2.5d;
                }
            }
        }
        return score;
    }

    private static double fodScore(List<PointF> points, ScoreContext scoreContext) {
        double score = 0d;
        float minimumDistance = scoreContext.iconSize * 1.05f;
        for (PointF point : points) {
            float distance = (float) Math.hypot(
                    point.x - scoreContext.fodX, point.y - scoreContext.fodY
            );
            if (distance < minimumDistance) {
                double overlap = minimumDistance - distance;
                score += overlap * overlap * 0.45d;
            }
        }
        return score;
    }

    private static List<PointF> clampPointsToSafeBounds(List<PointF> points,
                                                        ScoreContext scoreContext) {
        if (points.isEmpty()) {
            return new ArrayList<>();
        }
        float minimumX = Float.MAX_VALUE;
        float maximumX = -Float.MAX_VALUE;
        float minimumY = Float.MAX_VALUE;
        float maximumY = -Float.MAX_VALUE;
        for (PointF point : points) {
            minimumX = Math.min(minimumX, point.x);
            maximumX = Math.max(maximumX, point.x);
            minimumY = Math.min(minimumY, point.y);
            maximumY = Math.max(maximumY, point.y);
        }

        float shiftX = 0f;
        if (minimumX < scoreContext.minX && maximumX > scoreContext.maxX) {
            shiftX = (scoreContext.minX + scoreContext.maxX - minimumX - maximumX) / 2f;
        } else if (minimumX < scoreContext.minX) {
            shiftX = scoreContext.minX - minimumX;
        } else if (maximumX > scoreContext.maxX) {
            shiftX = scoreContext.maxX - maximumX;
        }

        float shiftY = 0f;
        if (minimumY < scoreContext.minY && maximumY > scoreContext.maxY) {
            shiftY = (scoreContext.minY + scoreContext.maxY - minimumY - maximumY) / 2f;
        } else if (minimumY < scoreContext.minY) {
            shiftY = scoreContext.minY - minimumY;
        } else if (maximumY > scoreContext.maxY) {
            shiftY = scoreContext.maxY - maximumY;
        }

        List<PointF> fitted = new ArrayList<>(points.size());
        for (PointF point : points) {
            fitted.add(new PointF(point.x + shiftX, point.y + shiftY));
        }
        return fitted;
    }

    private static PointF pointAt(float folderX, float folderY,
                                  float radius, double angle) {
        return new PointF(
                folderX + radius * (float) Math.cos(angle),
                folderY + radius * (float) Math.sin(angle)
        );
    }

    private static List<Float> buildAutoAngles() {
        List<Float> angles = new ArrayList<>(72);
        for (int index = 0; index < 72; index++) {
            angles.add(index * 5f);
        }
        return angles;
    }

    private static float resolveDirectionDeg(float outwardDeg, int directionMode,
                                             float directionOffsetDeg) {
        switch (directionMode) {
            case DIRECTION_INWARD:
                return normalizeDeg(outwardDeg + 180f);
            case DIRECTION_UP:
                return -90f;
            case DIRECTION_DOWN:
                return 90f;
            case DIRECTION_LEFT:
                return 180f;
            case DIRECTION_RIGHT:
                return 0f;
            case DIRECTION_CUSTOM:
                return normalizeDeg(outwardDeg + directionOffsetDeg);
            case DIRECTION_AUTO:
            case DIRECTION_OUTWARD:
            default:
                return normalizeDeg(outwardDeg);
        }
    }

    private static List<Integer> buildOrder(List<PointF> points,
                                            float folderX, float folderY,
                                            float directionDeg, int orderMode) {
        List<Integer> order = new ArrayList<>(points.size());
        for (int index = 0; index < points.size(); index++) {
            order.add(index);
        }
        switch (orderMode) {
            case ORDER_LEFT_TO_RIGHT:
                order.sort(Comparator.comparingDouble(index -> points.get(index).x));
                break;
            case ORDER_RIGHT_TO_LEFT:
                order.sort((first, second) -> Float.compare(
                        points.get(second).x, points.get(first).x
                ));
                break;
            case ORDER_OUTWARD:
                order.sort(Comparator.comparingDouble(index -> {
                    PointF point = points.get(index);
                    return Math.hypot(point.x - folderX, point.y - folderY);
                }));
                break;
            case ORDER_CENTER_OUT:
                order.sort(Comparator.comparingDouble(index -> {
                    PointF point = points.get(index);
                    double angle = Math.toDegrees(Math.atan2(
                            point.y - folderY, point.x - folderX
                    ));
                    return Math.abs(angularDistanceDeg((float) angle, directionDeg));
                }));
                break;
            case ORDER_GEOMETRY:
            default:
                break;
        }
        return order;
    }

    private static double angularDistanceDeg(float firstDeg, float secondDeg) {
        return Math.toDegrees(Math.abs(Math.atan2(
                Math.sin(Math.toRadians(firstDeg - secondDeg)),
                Math.cos(Math.toRadians(firstDeg - secondDeg))
        )));
    }

    private static float normalizeDeg(float degrees) {
        float normalized = degrees % 360f;
        return normalized > 180f ? normalized - 360f : normalized;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
