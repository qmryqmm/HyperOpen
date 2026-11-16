package io.github.qmryqmm.hyperopen.layout;

import android.graphics.PointF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 文件夹二级子项的固定圆环布局，真机与应用预览共享几何和动画顺序。 */
public final class FolderChildPacking {

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

    private static final class BoundsContext {
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;

        BoundsContext(float minX, float maxX, float minY, float maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private static final class Candidate {
        final List<PointF> points;
        final float radius;
        final float directionDeg;

        Candidate(List<PointF> points, float radius, float directionDeg) {
            this.points = points;
            this.radius = radius;
            this.directionDeg = directionDeg;
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

        BoundsContext boundsContext = new BoundsContext(minX, maxX, minY, maxY);
        float outwardDeg = (float) Math.toDegrees(Math.atan2(folderY - fodY, folderX - fodX));
        float preferredDeg = resolveDirectionDeg(
                outwardDeg, directionMode, directionOffsetDeg
        );
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

        Candidate selected = buildRing(
                count, folderX, folderY, radius, angleStepDeg, preferredDeg
        );

        List<PointF> fittedPoints = clampPointsToSafeBounds(selected.points, boundsContext);
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
                                       float directionDeg) {
        List<PointF> points = new ArrayList<>(count);
        double centerIndex = (count - 1) / 2d;
        double directionRad = Math.toRadians(directionDeg);
        double stepRad = Math.toRadians(angleStepDeg);
        for (int index = 0; index < count; index++) {
            double angle = directionRad + (index - centerIndex) * stepRad;
            points.add(pointAt(folderX, folderY, radius, angle));
        }
        return new Candidate(points, radius, directionDeg);
    }

    private static List<PointF> clampPointsToSafeBounds(List<PointF> points,
                                                        BoundsContext boundsContext) {
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
        if (minimumX < boundsContext.minX && maximumX > boundsContext.maxX) {
            shiftX = (boundsContext.minX + boundsContext.maxX - minimumX - maximumX) / 2f;
        } else if (minimumX < boundsContext.minX) {
            shiftX = boundsContext.minX - minimumX;
        } else if (maximumX > boundsContext.maxX) {
            shiftX = boundsContext.maxX - maximumX;
        }

        float shiftY = 0f;
        if (minimumY < boundsContext.minY && maximumY > boundsContext.maxY) {
            shiftY = (boundsContext.minY + boundsContext.maxY - minimumY - maximumY) / 2f;
        } else if (minimumY < boundsContext.minY) {
            shiftY = boundsContext.minY - minimumY;
        } else if (maximumY > boundsContext.maxY) {
            shiftY = boundsContext.maxY - maximumY;
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
