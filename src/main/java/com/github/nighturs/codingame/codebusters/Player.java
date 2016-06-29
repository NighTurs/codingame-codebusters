package com.github.nighturs.codingame.codebusters;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "NonFinalUtilityClass"})
class Player {
    static final int X_UNIT = 9000;
    static final int Y_UNIT = 16000;
    static final int FOG_UNIT = 2200;
    static final int BUSTER_SPEED = 800;
    static final int GOST_SPEED = 400;
    static final int TRAP_RANGE_INNER = 900;
    static final int TRAP_RANGE_OUTER = 1760;
    static final int STUN_RANGE = 1760;
    static final int BASE_RANGE = 1600;
    static final int STUN_COOLDOWN = 20;
    static final int TEST_END = 9999;

    static GameState gameState;
    private static Random random = new Random(1234);

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int bustersPerPlayer = in.nextInt();
        int ghostCount = in.nextInt();
        int myTeamId = in.nextInt();
        gameState = new GameState(bustersPerPlayer, ghostCount, myTeamId);

        //noinspection InfiniteLoopStatement
        while (true) {
            int entities = in.nextInt();
            if (entities == TEST_END) {
                break;
            }
            List<Buster> myBusters = new ArrayList<>();
            List<Buster> enemyBusters = new ArrayList<>();
            List<Gost> visibleGosts = new ArrayList<>();
            for (int i = 0; i < entities; i++) {
                int entityId = in.nextInt();
                int x = in.nextInt();
                int y = in.nextInt();
                int entityType = in.nextInt();
                int state = in.nextInt();
                int value = in.nextInt();
                if (entityType == -1) {
                    visibleGosts.add(Gost.create(entityId, reverseIfNeeded(Point.create(y, x)), value, state));
                } else {
                    Integer lastTurnUsedStun = gameState.getLastTurnUsedStun().get(entityId);
                    Buster buster = Buster.create(entityId,
                            reverseIfNeeded(Point.create(y, x)),
                            state == 1,
                            value,
                            state == 2,
                            state == 2 ? value : 0,
                            lastTurnUsedStun == null ?
                                    0 :
                                    Math.max(0, STUN_COOLDOWN - (gameState.getTurn() + 1 - lastTurnUsedStun)));
                    if (entityType == gameState.getMyTeamId()) {
                        myBusters.add(buster);
                    } else {
                        enemyBusters.add(buster);
                    }
                }
            }
            gameState.initTurn(myBusters, enemyBusters, visibleGosts);
            TurnPlan turnPlan = planTurn();
            gameState.finishTurn(turnPlan.strategies);
            System.out.print(turnPlan.formatTurn());
        }
    }

    public static TurnPlan planTurn() {
        List<Strategy> strategies = new ArrayList<>();
        strategies.addAll(WaitStunExpires.create(leftForStrategy(gameState.getMyBusters(),
                strategies,
                WaitStunExpires.class)));
        strategies.addAll(StunEnemy.create(leftForStrategy(gameState.getMyBusters(), strategies, StunEnemy.class)));
        strategies.addAll(ReleaseGostInBase.create(leftForStrategy(gameState.getMyBusters(),
                strategies,
                ReleaseGostInBase.class)));
        strategies.addAll(BringGostToBase.create(leftForStrategy(gameState.getMyBusters(),
                strategies,
                BringGostToBase.class)));
        strategies.addAll(Scout.create(leftForStrategy(gameState.getMyBusters(), strategies, Scout.class)));
        strategies.addAll(PrepareToCatchGost.create(leftForStrategy(gameState.getMyBusters(),
                strategies,
                PrepareToCatchGost.class)));
        strategies.addAll(CatchGost.create(leftForStrategy(gameState.getMyBusters(), strategies, CatchGost.class)));
        strategies.addAll(SearchForGost.create(leftForStrategy(gameState.getMyBusters(),
                strategies,
                SearchForGost.class)));

        Map<Integer, List<Strategy>> stratsByBuster =
                strategies.stream().collect(Collectors.groupingBy(a -> a.getBuster().getId()));

        List<Strategy> goFoStrats = new ArrayList<>();
        for (List<Strategy> strats : stratsByBuster.values()) {

            goFoStrats.add(strats.stream()
                    .sorted((a, b) -> Integer.compare(priority(a), priority(b)))
                    .findFirst()
                    .get());
        }

        return TurnPlan.create(goFoStrats);
    }

    static List<Buster> leftForStrategy(List<Buster> allBusters,
                                        List<Strategy> strategies,
                                        Class<? extends Strategy> strategy) {
        List<Buster> left = new ArrayList<>();
        for (Buster buster : allBusters) {
            boolean vacant = true;
            for (Strategy strat : strategies) {
                if (strat.getBuster().getId() == buster.getId() && priority(strat.getClass()) < priority(strategy)) {
                    vacant = false;
                }
            }
            if (vacant) {
                left.add(buster);
            }
        }
        return left;
    }

    static int priority(Class<? extends Strategy> s) {
        if (Objects.equals(s, ReleaseGostInBase.class)) {
            return 0;
        } else if (Objects.equals(s, BringGostToBase.class)) {
            return 1;
        } else if (Objects.equals(s, Scout.class)) {
            return 2;
        } else if (Objects.equals(s, PrepareToCatchGost.class)) {
            return 3;
        } else if (Objects.equals(s, CatchGost.class)) {
            return 4;
        } else if (Objects.equals(s, SearchForGost.class)) {
            return 5;
        } else if (Objects.equals(s, WaitStunExpires.class)) {
            return -100;
        } else if (Objects.equals(s, StunEnemy.class)) {
            return -99;
        }
        throw new RuntimeException("Unknown strategy");
    }

    static int priority(Strategy s) {
        return priority(s.getClass());
    }

    static Point reverseIfNeeded(Point p) {
        return gameState.shouldReverseCoordinates ? Point.create(X_UNIT - p.x, Y_UNIT - p.y) : p;
    }

    static int dist(Point a, Point b) {
        return (a.getX() - b.getX()) * (a.getX() - b.getX()) + (a.getY() - b.getY()) * (a.getY() - b.getY());
    }

    static int sqr(int distance) {
        return distance * distance;
    }

    static Point cap(int x, int y) {
        return Point.create(Math.min(Math.max(0, x), X_UNIT), Math.min(Math.max(0, y), Y_UNIT));
    }

    static Point moveInLine(Point a, Point b, double d, int mod, boolean ensureDistance) {
        int h = Math.abs(a.x - b.x);
        int w = Math.abs(a.y - b.y);
        int directionX = b.x - a.x >= 0 ? 1 : -1;
        int directionY = b.y - a.y >= 0 ? 1 : -1;
        int dd = dist(a, b);
        if (ensureDistance) {
            return cap(a.x + (int) Math.ceil(d * h / Math.sqrt(dd)) * directionX * mod,
                    a.y + (int) Math.ceil(d * w / Math.sqrt(dd)) * directionY * mod);
        } else {
            return cap((int)Math.round(a.x + d * h / Math.sqrt(dd) * directionX * mod),
                    (int)Math.round(a.y + d * w / Math.sqrt(dd) * directionY * mod));
        }
    }

    static Point moveToward(Point from, Point to, double d, boolean ensureDistance) {
        return moveInLine(from, to, d, 1, ensureDistance);
    }

    static Point moveAway(Point from, Point to, double d, boolean ensureDistance) {
        return moveInLine(from, to, d, -1, ensureDistance);
    }

    private static class WaitStunExpires implements Strategy {
        private final Buster buster;
        private final MoveBusterAction action;

        public static List<WaitStunExpires> create(List<Buster> busters) {
            List<WaitStunExpires> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (buster.isStunned()) {
                    strategies.add(new WaitStunExpires(buster, MoveBusterAction.create(buster, buster.getPoint())));
                }
            }
            return strategies;
        }

        public WaitStunExpires(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }
    }

    private static class StunEnemy implements Strategy {
        private static final int STUN_AGAIN_REMAINED_THRESHOLD = 2;
        private final Buster buster;
        private final StunBusterAction action;

        public static List<StunEnemy> create(List<Buster> busters) {
            final List<StunEnemy> strategies = new ArrayList<>();

            Map<Integer, List<Buster>> busterByEnemy = new HashMap<>();

            for (Buster enemy : gameState.getEnemyBusters()) {
                if (enemy.isStunned() && STUN_AGAIN_REMAINED_THRESHOLD < enemy.getUntilStunExpires()) {
                    continue;
                }
                for (Buster buster : busters) {
                    if (buster.isStunned() || buster.getUntilStunIsReady() > 0) {
                        continue;
                    }
                    int distance = dist(enemy.getPoint(), buster.getPoint());
                    if (distance <= sqr(STUN_RANGE)) {
                        List<Buster> whoCan = busterByEnemy.get(enemy.getId());
                        if (whoCan == null) {
                            whoCan = new ArrayList<>();
                        }
                        whoCan.add(buster);
                        busterByEnemy.put(enemy.getId(), whoCan);
                    }
                }
            }

            final Set<Integer> usedBusters = new HashSet<>();

            busterByEnemy.entrySet()
                    .stream()
                    .sorted((a, b) -> Integer.compare(a.getValue().size(), b.getValue().size()))
                    .forEach(kv -> {
                        Optional<Buster> whoCanOpt =
                                kv.getValue().stream().filter(x -> !usedBusters.contains(x.getId())).findFirst();
                        if (whoCanOpt.isPresent()) {
                            Buster whoCan = whoCanOpt.get();
                            strategies.add(new StunEnemy(whoCan,
                                    StunBusterAction.create(whoCan, gameState.getEnemyBusters()
                                            .stream()
                                            .filter(e -> e.getId() == kv.getKey())
                                            .findAny().get())));
                            usedBusters.add(whoCan.getId());
                        }
                    });

            return strategies;
        }

        public StunEnemy(Buster buster, StunBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("StunEnemy{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class ReleaseGostInBase implements Strategy {
        private final Buster buster;
        private final ReleaseBusterAction action;

        public static List<ReleaseGostInBase> create(List<Buster> busters) {
            List<ReleaseGostInBase> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (!buster.isCarryingGost) {
                    continue;
                }
                int distToBase = dist(buster.point, gameState.getMyBasePoint());
                if (distToBase > sqr(BASE_RANGE)) {
                    continue;
                } else {
                    strategies.add(new ReleaseGostInBase(buster, ReleaseBusterAction.create(buster)));
                }
            }
            return strategies;
        }

        public ReleaseGostInBase(Buster buster, ReleaseBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ReleaseGostInBase{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class BringGostToBase implements Strategy {
        private final Buster buster;
        private final MoveBusterAction action;

        public static List<BringGostToBase> create(List<Buster> busters) {
            List<BringGostToBase> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (!buster.isCarryingGost) {
                    continue;
                }
                int distToBase = dist(buster.point, gameState.getMyBasePoint());
                Point toward;
                if (distToBase > sqr(BASE_RANGE)) {
                    toward = moveToward(buster.getPoint(),
                            gameState.getMyBasePoint(),
                            Math.sqrt(distToBase) - BASE_RANGE, true);
                } else {
                    continue;
                }
                strategies.add(new BringGostToBase(buster, MoveBusterAction.create(buster, toward)));
            }
            return strategies;
        }

        private BringGostToBase(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("BringGostToBase{");
            sb.append("buster=").append(buster);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class CatchGost implements Strategy {
        private final Buster buster;
        private final Gost gost;
        private final BustBusterAction action;

        public static List<CatchGost> create(List<Buster> busters) {
            List<CatchGost> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                if (buster.isCarryingGost) {
                    continue;
                }
                Optional<Gost> gostOpt = gameState.visibleGosts.stream()
                        .filter(g -> dist(g.getPoint(), buster.getPoint()) <= sqr(TRAP_RANGE_OUTER) &&
                                dist(g.getPoint(), buster.getPoint()) >= sqr(TRAP_RANGE_INNER))
                        .sorted((a, b) -> Integer.compare(a.getStamina(), b.getStamina()))
                        .findFirst();
                if (!gostOpt.isPresent()) {
                    continue;
                }
                strategies.add(new CatchGost(buster, gostOpt.get(), new BustBusterAction(buster, gostOpt.get())));
            }
            return strategies;
        }

        private CatchGost(Buster buster, Gost gost, BustBusterAction action) {
            this.buster = buster;
            this.gost = gost;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("CatchGost{");
            sb.append("buster=").append(buster);
            sb.append(", gost=").append(gost);
            sb.append(", action=").append(action);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class PrepareToCatchGost implements Strategy {
        private static final int BIG_CATH_TRESHOLD = 4;
        private static final int STEPS_TO_REDUCE_TRESHOLD = 2;
        private final MoveBusterAction action;
        private final Buster buster;
        private final Gost goingForGost;

        public static List<PrepareToCatchGost> create(List<Buster> busters) {
            List<PrepareToCatchGost> strategies = new ArrayList<>();

            List<Pair<Buster, Gost>> vars = new ArrayList<>();
            for (Buster buster : busters) {
                for (Gost gost : gameState.getEstimatedGosts()) {
                    vars.add(Pair.create(buster, gost));
                }
            }

            Function<Pair<Buster, Gost>, Integer> costFunction = (Pair<Buster, Gost> p) -> {
                Buster buster = p.getFirst();
                Gost gost = p.getSecond();
                return gost.getStamina() + turnsToPrepare(buster, gost);
            };

            vars.sort((a, b) -> Integer.compare(costFunction.apply(a),
                    costFunction.apply(b)));

            Set<Integer> pickedGosts = new HashSet<>();
            Set<Integer> pickedBusters = new HashSet<>();
            List<MutablePair<Gost, Integer>> alreadyPickedGosts = new ArrayList<>();

            OUTER_LOOP:
            for (Pair<Buster, Gost> var : vars) {
                if (pickedBusters.contains(var.getFirst().getId()) || pickedGosts.contains(var.getSecond().getId())) {
                    continue OUTER_LOOP;
                } else {
                    Buster buster = var.getFirst();
                    Gost gost = var.getSecond();
                    if (gost.getStamina() > BIG_CATH_TRESHOLD) {
                        for (MutablePair<Gost, Integer> alreadyPicked : alreadyPickedGosts) {
                            Gost pickedGost = alreadyPicked.getFirst();
                            int alreadCatchedBy = alreadyPicked.getSecond();
                            int willReduceSteps =
                                    (pickedGost.getStamina() - alreadCatchedBy * turnsToPrepare(buster, pickedGost)) /
                                            (alreadCatchedBy + 1);
                            if (willReduceSteps >= STEPS_TO_REDUCE_TRESHOLD) {
                                pickedBusters.add(buster.getId());
                                alreadyPicked.setSecond(alreadCatchedBy + 1);
                                Optional<PrepareToCatchGost> prep = goForGost(buster, pickedGost);
                                if (prep.isPresent()) {
                                    strategies.add(prep.get());
                                }
                                continue OUTER_LOOP;
                            }
                        }
                    }

                    pickedBusters.add(buster.getId());
                    pickedGosts.add(gost.getId());

                    Optional<PrepareToCatchGost> prep = goForGost(buster, gost);
                    if (prep.isPresent()) {
                        strategies.add(prep.get());
                    }

                    alreadyPickedGosts.add(MutablePair.create(gost, 1));
                }
            }
            return strategies;
        }

        private static Optional<PrepareToCatchGost> goForGost(Buster buster, Gost gost) {
            // Means we should do catch instead of prepare
            if (turnsToPrepare(buster, gost) == 0) {
                return Optional.empty();
            }
            int distance = dist(buster.point, gost.escapingTo());
            Point toward = null;
            if (distance > sqr(TRAP_RANGE_OUTER)) {
                toward = moveToward(buster.point,
                        gost.escapingTo(),
                        Math.sqrt(distance) - TRAP_RANGE_OUTER,
                        true);
            } else if (distance < sqr(TRAP_RANGE_INNER)) {
                toward =
                        moveAway(buster.point, gost.escapingTo(), TRAP_RANGE_INNER - Math.sqrt(distance), true);
            } else {
                // Gost already moves towards us
                toward = buster.getPoint();
            }
            return Optional.of(new PrepareToCatchGost(buster, gost, MoveBusterAction.create(buster, toward)));
        }

        private static int turnsToPrepare(Buster buster, Gost gost) {
            int prepateTurns = 0;
            int momentDist = dist(buster.point, gost.getPoint());

            if (momentDist <= sqr(TRAP_RANGE_OUTER) && momentDist >= sqr(TRAP_RANGE_INNER)) {
                prepateTurns = 0;
            } else {
                int distance = dist(buster.point, gost.escapingTo());
                if (distance > sqr(TRAP_RANGE_OUTER)) {
                    prepateTurns = (int) Math.ceil((Math.sqrt(distance) - TRAP_RANGE_OUTER) / BUSTER_SPEED);
                } else if (distance < sqr(TRAP_RANGE_INNER)) {
                    prepateTurns = (int) Math.ceil((TRAP_RANGE_INNER - Math.sqrt(distance)) / BUSTER_SPEED);
                }
            }
            return prepateTurns;
        }

        public PrepareToCatchGost(Buster buster, Gost goingForGost, MoveBusterAction action) {
            this.action = action;
            this.goingForGost = goingForGost;
            this.buster = buster;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PrepareToCatchGost{");
            sb.append("action=").append(action);
            sb.append(", buster=").append(buster);
            sb.append(", goingForGost=").append(goingForGost);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class SearchForGost implements Strategy {
        private final MoveBusterAction action;
        private final Buster buster;

        public static List<SearchForGost> create(List<Buster> busters) {
            List<SearchForGost> strategies = new ArrayList<>();
            for (Buster buster : busters) {
                Optional<Point> unvisitedPoint = gameState.getGrid().getCloseUnvisitedPoint(buster);
                if (unvisitedPoint.isPresent()) {
                    strategies.add(new SearchForGost(buster,
                            MoveBusterAction.create(buster, unvisitedPoint.get())));
                    continue;
                }

                List<Strategy> strats = gameState.getPrevTurnStrats();
                for (Strategy strat : strats) {
                    if (strat instanceof SearchForGost) {
                        SearchForGost prevSearch = ((SearchForGost) strat);
                        if (prevSearch.buster.getId() == buster.getId() &&
                                dist(prevSearch.action.getToPoint(), buster.getPoint()) >= sqr(BUSTER_SPEED)) {
                            strategies.add(new SearchForGost(buster,
                                    MoveBusterAction.create(buster, prevSearch.action.getToPoint())));
                        }
                    }
                }
                strategies.add(new SearchForGost(buster,
                        MoveBusterAction.create(buster, Point.create(random.nextInt(X_UNIT), random.nextInt(Y_UNIT)))));
            }
            return strategies;
        }

        private SearchForGost(Buster buster, MoveBusterAction action) {
            this.buster = buster;
            this.action = action;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("SearchForGost{");
            sb.append("action=").append(action);
            sb.append(", buster=").append(buster);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class Scout implements Strategy {
        public final static int MAX_TURNS_VALID = 10;
        private final MoveBusterAction action;
        private final Buster buster;

        public static List<Scout> create(List<Buster> busters) {
            List<Scout> strategies = new ArrayList<>();
            if (gameState.getTurn() > MAX_TURNS_VALID) {
                return strategies;
            }

            Set<Integer> busyBusters = new HashSet<>();

            for (GridCell cell : gameState.getGrid().getScoutCells()) {
                if (cell.isEverVisited()) {
                    continue;
                }
                int minDist = Integer.MAX_VALUE;
                Buster bestBuster = null;
                for (Buster buster : busters) {
                    if (busyBusters.contains(buster.getId())) {
                        continue;
                    }
                    int distance = dist(buster.getPoint(), cell.busterShouldMoveTo(buster));
                    if (minDist > distance) {
                        minDist = distance;
                        bestBuster = buster;
                    }
                }
                if (bestBuster != null) {
                    busyBusters.add(bestBuster.getId());
                    strategies.add(new Scout(MoveBusterAction.create(bestBuster, cell.busterShouldMoveTo(bestBuster)),
                            bestBuster));
                }
            }
            return strategies;
        }

        public Scout(MoveBusterAction action, Buster buster) {
            this.action = action;
            this.buster = buster;
        }

        @Override
        public BusterAction busterAction() {
            return action;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Scout{");
            sb.append("action=").append(action);
            sb.append(", buster=").append(buster);
            sb.append('}');
            return sb.toString();
        }
    }

    private interface Strategy {

        BusterAction busterAction();
        Buster getBuster();
    }

    static class GameState {
        // fixed for game
        private final int bustersPerPlayer;
        private final int ghostCount;
        private final int myTeamId;
        private final Point myBasePoint;
        private final boolean shouldReverseCoordinates;
        // fixed for turn
        private List<Buster> myBusters;
        private List<Buster> enemyBusters;
        private List<Gost> visibleGosts;
        // memory
        private int turn = -1;
        final private Grid grid;
        private List<Strategy> prevTurnStrats = Collections.emptyList();
        private Map<Integer, Integer> lastTurnUsedStun = new HashMap<>();
        private Map<Integer, Gost> estimatedGosts = new HashMap<>();

        public GameState(int bustersPerPlayer, int ghostCount, int myTeamId) {
            this.bustersPerPlayer = bustersPerPlayer;
            this.ghostCount = ghostCount;
            this.myTeamId = myTeamId;
            this.myBasePoint = Point.create(0, 0);
            this.shouldReverseCoordinates = myTeamId != 0;
            this.grid = Grid.create();
        }

        @SuppressWarnings("ParameterHidesMemberVariable")
        public void initTurn(List<Buster> myBusters, List<Buster> enemyBusters, List<Gost> visibleGosts) {
            this.turn++;
            this.myBusters = myBusters;
            this.enemyBusters = enemyBusters;
            this.visibleGosts = visibleGosts;
            updateWithRealGostPositions();
            removeFalseGostEstimations();
            grid.updateVisits(myBusters);
        }

        public void updateWithRealGostPositions() {
            for (Gost gost : visibleGosts) {
                estimatedGosts.put(gost.getId(), gost);
            }
        }

        public void finishTurn(List<Strategy> strategies) {
            this.prevTurnStrats = strategies;
            for (Strategy strategy : strategies) {
                if (strategy instanceof StunEnemy) {
                    lastTurnUsedStun.put(strategy.getBuster().getId(), getTurn());
                }
            }
            estimateGostPositions();
        }

        private void removeFalseGostEstimations() {
            Set<Integer> visibleGostsIds = visibleGosts.stream().map(Gost::getId).collect(Collectors.toSet());
            List<Gost> toRemove = new ArrayList<>();
            for (Gost gost : estimatedGosts.values()) {
                if (visibleGostsIds.contains(gost.getId())) {
                    continue;
                }
                for (Buster buster : myBusters) {
                    int distance = dist(buster.getPoint(), gost.getPoint());
                    if (distance <= sqr(FOG_UNIT)) {
                        toRemove.add(gost);
                    }
                }
            }
            for (Gost gostToRemove : toRemove) {
                estimatedGosts.remove(gostToRemove.getId());
            }
        }

        private void estimateGostPositions() {
            for (Gost gost : visibleGosts) {
                Gost futureTurnsGost =
                        Gost.create(gost.getId(), gost.escapingTo(), gost.getTrapAttempts(), gost.getStamina());
                estimatedGosts.put(futureTurnsGost.getId(), futureTurnsGost);
            }
        }

        public int getBustersPerPlayer() {
            return bustersPerPlayer;
        }

        public int getGhostCount() {
            return ghostCount;
        }

        public int getMyTeamId() {
            return myTeamId;
        }

        public List<Buster> getMyBusters() {
            return myBusters;
        }

        public List<Buster> getEnemyBusters() {
            return enemyBusters;
        }

        public List<Gost> getVisibleGosts() {
            return visibleGosts;
        }

        public List<Strategy> getPrevTurnStrats() {
            return prevTurnStrats;
        }

        public Point getMyBasePoint() {
            return myBasePoint;
        }

        public int getTurn() {
            return turn;
        }

        public Map<Integer, Integer> getLastTurnUsedStun() {
            return lastTurnUsedStun;
        }

        public Collection<Gost> getEstimatedGosts() {
            return estimatedGosts.values();
        }

        public boolean shouldReverseCoordinates() {
            return shouldReverseCoordinates;
        }

        public Grid getGrid() {
            return grid;
        }
    }

    static class Grid {
        public static final int GRID_CELL = 1000;
        public static final int GRID_N = X_UNIT / GRID_CELL;
        public static final int GRID_M = Y_UNIT / GRID_CELL;
        private GridCell[][] grid;
        // sorted in priority order
        private List<GridCell> scoutCells;

        public static Grid create() {
            GridCell[][] grid = new GridCell[GRID_N][GRID_M];
            for (int i = 0; i < GRID_N; i++) {
                for (int h = 0; h < GRID_M; h++) {
                    grid[i][h] = new GridCell(i, h);
                }
            }
            grid[0][0].setLastVisitTurn(0);
            List<GridCell> scouteCells = Arrays.asList(grid[6][7], grid[2][10], grid[7][2]);
            return new Grid(grid, scouteCells);
        }

        public Grid(GridCell[][] grid, List<GridCell> scoutCells) {
            this.grid = grid;
            this.scoutCells = scoutCells;
        }

        public void updateVisits(List<Buster> busters) {
            for (int i = 0; i < GRID_N; i++) {
                for (int h = 0; h < GRID_M; h++) {
                    for (Buster buster : busters) {
                        if (grid[i][h].busterSeesIt(buster)) {
                            grid[i][h].setLastVisitTurn(gameState.getTurn());
                        }
                    }
                }
            }
        }

        public Optional<Point> getCloseUnvisitedPoint(Buster buster) {
            int min = Integer.MAX_VALUE;
            Point p = buster.getPoint();
            GridCell closestCell = null;
            for (int i = 0; i < GRID_N; i++) {
                for (int h = 0; h < GRID_M; h++) {
                    if (!grid[i][h].isEverVisited()) {
                        GridCell c = grid[i][h];
                        if (min > dist(p, c.botRight)) {
                            min = dist(p, c.botRight);
                            closestCell = c;
                        }
                    }
                }
            }
            return Optional.ofNullable(closestCell).map(x -> x.busterShouldMoveTo(buster));
        }

        public List<GridCell> getScoutCells() {
            return scoutCells;
        }
    }

    static class GridCell {

        final int x;
        final int y;
        final Point topLeft;
        final Point topRight;
        final Point botLeft;
        final Point botRight;
        int lastVisitTurn = -1;

        public GridCell(int x, int y) {
            this.x = x;
            this.y = y;
            int gridCell = Grid.GRID_CELL;
            topLeft = Point.create(x * gridCell, y * gridCell);
            topRight = Point.create(x * gridCell, y * gridCell + gridCell);
            botLeft = Point.create(x * gridCell + gridCell, y * gridCell);
            botRight = Point.create(x * gridCell + gridCell, y * gridCell + gridCell);
        }

        public boolean busterSeesIt(Buster buster) {
            Point bp = buster.getPoint();
            if (dist(bp, topLeft) <= sqr(FOG_UNIT) && dist(bp, topRight) <= sqr(FOG_UNIT) &&
                    dist(bp, botLeft) <= sqr(FOG_UNIT) && dist(bp, botRight) <= sqr(FOG_UNIT)) {
                return true;
            }
            return false;
        }

        public Point busterShouldMoveTo(Buster buster) {
            Point p = buster.getPoint();
            Point b = topLeft;
            int max = dist(p, topLeft);
            if (max < dist(p, topRight)) {
                b = topRight;
                max = dist(p, topRight);
            }
            if (max < dist(p, botLeft)) {
                b = botLeft;
                max = dist(p, botLeft);
            }
            if (max < dist(p, botRight)) {
                b = botRight;
            }
            return b;
        }


        public boolean isEverVisited() {
            return lastVisitTurn != -1;
        }

        public Point getTopLeft() {
            return topLeft;
        }

        public Point getTopRight() {
            return topRight;
        }

        public Point getBotLeft() {
            return botLeft;
        }

        public Point getBotRight() {
            return botRight;
        }

        public int getLastVisitTurn() {
            return lastVisitTurn;
        }

        public void setLastVisitTurn(int lastVisitTurn) {
            this.lastVisitTurn = lastVisitTurn;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("GridCell{");
            sb.append("x=").append(x);
            sb.append(", y=").append(y);
            sb.append(", topLeft=").append(topLeft);
            sb.append(", topRight=").append(topRight);
            sb.append(", botLeft=").append(botLeft);
            sb.append(", botRight=").append(botRight);
            sb.append(", lastVisitTurn=").append(lastVisitTurn);
            sb.append('}');
            return sb.toString();
        }
    }

    private static final class TurnPlan {
        private final List<Strategy> strategies;

        private static TurnPlan create(List<Strategy> strategies) {
            return new TurnPlan(strategies);
        }

        private TurnPlan(List<Strategy> strategies) {
            this.strategies = strategies;
        }

        public String formatTurn() {
            StringBuilder sb = new StringBuilder();
            strategies.forEach(x -> System.err.println(x));
            strategies.stream()
                    .map(Strategy::busterAction)
                    .sorted((a, b) -> Integer.compare(a.getBuster().getId(), b.getBuster().getId()))
                    .forEach(x -> sb.append(x.formatLine()).append("\n"));
            return sb.toString();
        }
    }

    private static final class StunBusterAction implements BusterAction {
        private final Buster buster;
        private final Buster enemyBuster;

        public static StunBusterAction create(Buster buster, Buster enemyBuster) {
            return new StunBusterAction(buster, enemyBuster);
        }

        public StunBusterAction(Buster buster, Buster enemyBuster) {
            this.buster = buster;
            this.enemyBuster = enemyBuster;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String formatLine() {
            return String.format("STUN %s", enemyBuster.getId());
        }
    }

    private static final class ReleaseBusterAction implements BusterAction {

        private final Buster buster;

        public static ReleaseBusterAction create(Buster buster) {
            return new ReleaseBusterAction(buster);
        }

        private ReleaseBusterAction(Buster buster) {
            this.buster = buster;
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        @Override
        public String formatLine() {
            return "RELEASE";
        }
    }

    private static final class BustBusterAction implements BusterAction {
        private final Buster buster;
        private final Gost gost;

        public static BustBusterAction create(Buster buster, Gost gost) {
            return new BustBusterAction(buster, gost);
        }

        private BustBusterAction(Buster buster, Gost gost) {
            this.buster = buster;
            this.gost = gost;
        }

        @Override
        public String formatLine() {
            return String.format("BUST %s", gost.getId());
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        public Gost getGost() {
            return gost;
        }
    }

    private static final class MoveBusterAction implements BusterAction {

        private final Buster buster;
        private final Point toPoint;

        public static MoveBusterAction create(Buster buster, Point toPoint) {
            return new MoveBusterAction(buster, toPoint);
        }

        private MoveBusterAction(Buster buster, Point toPoint) {
            this.buster = buster;
            this.toPoint = toPoint;
        }

        @Override
        public String formatLine() {
            Point actualPoint = reverseIfNeeded(toPoint);
            return String.format("MOVE %s %s", actualPoint.getY(), actualPoint.getX());
        }

        @Override
        public Buster getBuster() {
            return buster;
        }

        public Point getToPoint() {
            return toPoint;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("MoveBusterAction{");
            sb.append("toPoint=").append(toPoint);
            sb.append('}');
            return sb.toString();
        }
    }

    private interface BusterAction {
        Buster getBuster();
        String formatLine();
    }

    static final class Buster {
        private final int id;
        private final Point point;
        private final boolean isCarryingGost;
        private final int carriedGostId;
        private final boolean isStunned;
        private final int untilStunExpires;
        private final int untilStunIsReady;

        public static Buster create(int id,
                                    Point point,
                                    boolean isCarryingGost,
                                    int carriedGostId,
                                    boolean isStunned,
                                    int untilStunExpires,
                                    int untilStunIsReady) {
            return new Buster(id, point, isCarryingGost, carriedGostId, isStunned, untilStunExpires, untilStunIsReady);
        }

        public Buster(int id,
                      Point point,
                      boolean isCarryingGost,
                      int carriedGostId,
                      boolean isStunned,
                      int untilStunExpires,
                      int untilStunIsReady) {
            this.id = id;
            this.point = point;
            this.isCarryingGost = isCarryingGost;
            this.carriedGostId = carriedGostId;
            this.isStunned = isStunned;
            this.untilStunExpires = untilStunExpires;
            this.untilStunIsReady = untilStunIsReady;
        }

        public int getId() {
            return id;
        }

        public Point getPoint() {
            return point;
        }

        public boolean isCarryingGost() {
            return isCarryingGost;
        }

        public int getCarriedGostId() {
            return carriedGostId;
        }

        public boolean isStunned() {
            return isStunned;
        }

        public int getUntilStunExpires() {
            return untilStunExpires;
        }

        public int getUntilStunIsReady() {
            return untilStunIsReady;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Buster{");
            sb.append("id=").append(id);
            sb.append(", point=").append(point);
            sb.append(", isCarryingGost=").append(isCarryingGost);
            sb.append(", carriedGostId=").append(carriedGostId);
            sb.append(", isStunned=").append(isStunned);
            sb.append(", untilStunExpires=").append(untilStunExpires);
            sb.append(", untilStunIsReady=").append(untilStunIsReady);
            sb.append('}');
            return sb.toString();
        }
    }

    static final class Gost {
        private final int id;
        private final Point point;
        private final int trapAttempts;
        private final int stamina;

        static Gost create(int id, Point point, int trapAttempts, int stamina) {

            return new Gost(id, point, trapAttempts, stamina);
        }

        public Gost(int id, Point point, int trapAttempts, int stamina) {
            this.id = id;
            this.point = point;
            this.trapAttempts = trapAttempts;
            this.stamina = stamina;
        }

        public Point escapingTo() {
            int x = 0, y = 0;
            int count = 0;
            for (Buster buster : gameState.enemyBusters) {
                if (dist(buster.getPoint(), point) <= sqr(FOG_UNIT)) {
                    x += buster.point.getX();
                    y += buster.point.getY();
                    count++;
                }
            }
            for (Buster buster : gameState.myBusters) {
                if (dist(buster.getPoint(), point) <= sqr(FOG_UNIT)) {
                    x += buster.point.getX();
                    y += buster.point.getY();
                    count++;
                }
            }
            if (count == 0) {
                return point;
            }

            int meanX = x / count;
            int meanY = y / count;

            return moveAway(point, Point.create(meanX, meanY), GOST_SPEED, false);
        }

        public int getId() {
            return id;
        }

        public Point getPoint() {
            return point;
        }

        public int getTrapAttempts() {
            return trapAttempts;
        }

        public int getStamina() {
            return stamina;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Gost{");
            sb.append("id=").append(id);
            sb.append(", point=").append(point);
            sb.append(", trapAttempts=").append(trapAttempts);
            sb.append(", escapingTo=").append(escapingTo());
            sb.append('}');
            return sb.toString();
        }
    }

    interface PointBase {
        int getX();
        int getY();
    }

    static final class Point implements PointBase {
        final int x, y;

        public static Point create(int x, int y) {
            return new Point(x, y);
        }

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Point point = (Point) o;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public String toString() {
            Point actualPoint = reverseIfNeeded(this);
            final StringBuilder sb = new StringBuilder("Point{");
            sb.append("x=").append(actualPoint.x);
            sb.append(", y=").append(actualPoint.y);
            sb.append('}');
            return sb.toString();
        }
    }

    static final class Pair<K, V> {
        private final K first;
        private final V second;
        public static <K, V> Pair<K, V> create(K first, V second) {
            return new Pair<>(first, second);
        }

        private Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public K getFirst() {
            return first;
        }

        public V getSecond() {
            return second;
        }
    }

    static final class MutablePair<K, V> {
        private final K first;
        private V second;
        public static <K, V> MutablePair<K, V> create(K first, V second) {
            return new MutablePair<K, V>(first, second);
        }

        private MutablePair(K first, V second) {
            this.first = first;
            this.second = second;
        }

        public K getFirst() {
            return first;
        }

        public V getSecond() {
            return second;
        }

        public void setSecond(V second) {
            this.second = second;
        }
    }
}