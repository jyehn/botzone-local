
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.Arrays;
import java.util.Random;

// 场地上带有坐标的物件
class FieldProp {
    int row, col;
}

// 场地上的玩家
class Player extends FieldProp {
    int strength = 0;
    int powerUpLeft = 0;
    boolean dead = false;
}

// 回合新产生的豆子的坐标
class NewFruits {
    public static final int MAX_GENERATOR_COUNT = 4;
    FieldProp newFruits[] = new FieldProp[MAX_GENERATOR_COUNT * 8];
    int newFruitCount = 0;

    NewFruits() {
        for (int i = 0; i < newFruits.length; i++)
            newFruits[i] = new FieldProp();
    }
}

// 状态转移记录结构
class TurnStateTransfer {
    public static final int MAX_PLAYER_COUNT = 4;
    // 玩家选定的动作
    int[] actions = new int[MAX_PLAYER_COUNT];

    // 此回合该玩家的状态变化
    int[] change = new int[MAX_PLAYER_COUNT];

    // 此回合该玩家的力量变化
    int[] strengthDelta = new int[MAX_PLAYER_COUNT];

    TurnStateTransfer() {
        init();
    }

    void init() {
        Arrays.fill(strengthDelta, 0);
        Arrays.fill(actions, 0);
        Arrays.fill(change, 0);
    }
}

// 游戏主要逻辑处理类，包括输入输出、回合演算、状态转移，全局唯一
class GameField {
    public static final int FIELD_MAX_HEIGHT = 20;
    public static final int FIELD_MAX_WIDTH = 20;
    public static int MAX_GENERATOR_COUNT = 4;
    public static final int MAX_PLAYER_COUNT = 4;
    public static final int MAX_TURN = 100;

    public static final int[] dx = {0, 1, 0, -1, 1, 1, -1, -1}, dy = {-1, 0, 1, 0, -1, 1, 1, -1};
    // GridContentType
    public static final int empty = 0; // 其实不会用到
    public static final int player1 = 1; // 1号玩家
    public static final int player2 = 2; // 2号玩家
    public static final int player3 = 4; // 3号玩家
    public static final int player4 = 8; // 4号玩家
    public static final int playerMask = 1 | 2 | 4 | 8; // 用于检查有没有玩家等
    public static final int smallFruit = 16; // 小豆子
    public static final int largeFruit = 32; // 大豆子

    static int[] playerID2Mask = {player1, player2, player3, player4};
    static String[] playerID2str = {"0", "1", "2", "3"};

    // region GridStaticType
    public static int emptyWall = 0; // 其实不会用到
    public static int wallNorth = 1; // 北墙（纵坐标减少的方向）
    public static int wallEast = 2; // 东墙（横坐标增加的方向）
    public static int wallSouth = 4; // 南墙（纵坐标增加的方向）
    public static int wallWest = 8; // 西墙（横坐标减少的方向）
    public static int generator = 16; // 豆子产生器
    //endregion

    // 用移动方向换取这个方向上阻挡着的墙的二进制位
    static int[] direction2OpposingWall = {wallNorth, wallEast, wallSouth, wallWest};

    // region 方向，可以代入dx、dy数组，同时也可以作为玩家的动作
    public static int stay = -1;
    public static int up = 0;
    public static int right = 1;
    public static int down = 2;
    public static int left = 3;
    // 下面的这几个只是为了产生器程序方便，不会实际用到
    public static int ur = 4; // 右上
    public static int dr = 5; // 右下
    public static int dl = 6; // 左下
    public static int ul = 7; // 左上

    // endregion

    // region StatusChange
    public int none = 0;
    public static int ateSmall = 1;
    public static int ateLarge = 2;
    public static int powerUpCancel = 4;
    public static int die = 8;
    public static int error = 16;

    // endregion


    static int newFruitsCount = 0;

    NewFruits[] newFruits = new NewFruits[MAX_TURN];

    // 记录每回合的变化（栈）
    TurnStateTransfer[] backtrack = new TurnStateTransfer[MAX_TURN];

    int height, width;
    int generatorCount;
    int GENERATOR_INTERVAL, LARGE_FRUIT_DURATION, LARGE_FRUIT_ENHANCEMENT;

    // 场地格子固定的内容
    int fieldStatic[][] = new int[FIELD_MAX_HEIGHT][FIELD_MAX_WIDTH];

    // 场地格子会变化的内容
    int fieldContent[][] = new int[FIELD_MAX_HEIGHT][FIELD_MAX_WIDTH];
    int generatorTurnLeft; // 多少回合后产生豆子
    int aliveCount; // 有多少玩家存活
    int smallFruitCount;
    int turnID;
    FieldProp generators[] = new FieldProp[MAX_GENERATOR_COUNT]; // 有哪些豆子产生器
    Player players[] = new Player[MAX_PLAYER_COUNT]; // 有哪些玩家

    // 玩家选定的动作
    int actions[] = new int[MAX_PLAYER_COUNT];

    GameField() {
        for (int i = 0; i < backtrack.length; i++)
            backtrack[i] = new TurnStateTransfer();
        for (int i = 0; i < generators.length; i++)
            generators[i] = new FieldProp();
        for (int i = 0; i < players.length; i++)
            players[i] = new Player();
        for (int i = 0; i < newFruits.length; i++)
            newFruits[i] = new NewFruits();
    }

    // 判断指定玩家向指定方向移动是不是合法的（没有撞墙）
    boolean ActionValid(int playerID, int dir) {
        if (dir == stay) return true;
        Player p = players[playerID];
        int s = fieldStatic[p.row][p.col];
        return dir >= -1 && dir < 4 && (s & direction2OpposingWall[dir]) == 0;
    }

    boolean popState() {
        if (turnID <= 0) return false;

        TurnStateTransfer bt = backtrack[--turnID];
        int i, id;

        // 倒着来恢复状态

        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p_ = players[id];
            int change = bt.change[id];

            if (!p_.dead) {
                // 5. 大豆回合恢复
                if ((p_.powerUpLeft != 0) || ((change & powerUpCancel) != 0)) p_.powerUpLeft++;

                // 4. 吐出豆子
                if ((change & ateSmall) != 0) {
                    fieldContent[p_.row][p_.col] |= smallFruit;
                    smallFruitCount++;
                } else if ((change & ateLarge) != 0) {
                    fieldContent[p_.row][p_.col] |= largeFruit;
                    p_.powerUpLeft -= LARGE_FRUIT_DURATION;
                }
            }

            // 2. 魂兮归来
            if ((change & die) != 0) {
                p_.dead = false;
                aliveCount++;
                fieldContent[p_.row][p_.col] |= playerID2Mask[id];
            }

            // 1. 移形换影
            if (!p_.dead && bt.actions[id] != stay) {
                fieldContent[p_.row][p_.col] &= ~playerID2Mask[id];
                p_.row = (p_.row - dy[bt.actions[id]] + height) % height;
                p_.col = (p_.col - dx[bt.actions[id]] + width) % width;
                fieldContent[p_.row][p_.col] |= playerID2Mask[id];
            }

            // 0. 救赎不合法的灵魂
            if ((change & error) != 0) {
                p_.dead = false;
                aliveCount++;
                fieldContent[p_.row][p_.col] |= playerID2Mask[id];
            }

            // *. 恢复力量
            if (!p_.dead) p_.strength -= bt.strengthDelta[id];
        }

        // 3. 收回豆子
        if (generatorTurnLeft == GENERATOR_INTERVAL) {
            generatorTurnLeft = 1;
            NewFruits fruits = newFruits[--newFruitsCount];
            for (i = 0; i < fruits.newFruitCount; i++) {
                fieldContent[fruits.newFruits[i].row][fruits.newFruits[i].col] &= ~smallFruit;
                smallFruitCount--;
            }
        } else generatorTurnLeft++;

        return true;
    }

    // 在向actions写入玩家动作后，演算下一回合局面，并记录之前所有的场地状态，可供日后恢复。
    // 是终局的话就返回false
    boolean nextTurn() {
        int id, i, j;
        TurnStateTransfer bt = backtrack[turnID];
        bt.init();
        // 0. 杀死不合法输入
        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p = players[id];
            if (!p.dead) {
                int action = actions[id];
                if (action == stay) {
                    continue;
                }

                if (!ActionValid(id, action)) {
                    bt.strengthDelta[id] += -p.strength;
                    bt.change[id] = error;
                    fieldContent[p.row][p.col] &= ~playerID2Mask[id];
                    p.strength = 0;
                    p.dead = true;
                    aliveCount--;
                } else {
                    // 遇到比自己强♂壮的玩家是不能前进的
                    int target = fieldContent[(p.row + dy[action] + height) % height][(p.col + dx[action] + width) % width];
                    if ((target & playerMask) != 0) for (i = 0; i < MAX_PLAYER_COUNT; i++)
                        if (((target & playerID2Mask[i]) != 0) && players[i].strength > p.strength)
                            actions[id] = stay;
                }
            }
        }

        // 1. 位置变化
        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p_ = players[id];
            if (p_.dead) continue;

            bt.actions[id] = actions[id];

            if (actions[id] == stay) continue;

            // 移动
            fieldContent[p_.row][p_.col] &= ~playerID2Mask[id];
            p_.row = (p_.row + dy[actions[id]] + height) % height;
            p_.col = (p_.col + dx[actions[id]] + width) % width;
            fieldContent[p_.row][p_.col] |= playerID2Mask[id];
        }

        // 2. 玩家互殴
        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p_ = players[id];
            if (p_.dead) continue;

            // 判断是否有玩家在一起
            int player, containedCount = 0;
            int[] containedPlayers = new int[MAX_PLAYER_COUNT];
            for (player = 0; player < MAX_PLAYER_COUNT; player++)
                if ((fieldContent[p_.row][p_.col] & playerID2Mask[player]) != 0)
                    containedPlayers[containedCount++] = player;

            if (containedCount > 1) {
                // NAIVE bubble sort
                for (i = 0; i < containedCount; i++)
                    for (j = 0; j < containedCount - i - 1; j++)
                        if (players[containedPlayers[j]].strength < players[containedPlayers[j + 1]].strength) {
                            int t = containedPlayers[j];
                            containedPlayers[j] = containedPlayers[j + 1];
                            containedPlayers[j] = t;
                        }

                int begin;
                for (begin = 1; begin < containedCount; begin++)
                    if (players[containedPlayers[begin - 1]].strength > players[containedPlayers[begin]].strength)
                        break;

                // 这些玩家将会被杀死
                int lootedStrength = 0;
                for (i = begin; i < containedCount; i++) {
                    int id_ = containedPlayers[i];
                    Player p = players[id_];

                    // 从格子上移走
                    fieldContent[p.row][p.col] &= ~playerID2Mask[id];
                    p.dead = true;
                    int drop = p.strength / 2;
                    bt.strengthDelta[id_] += -drop;
                    bt.change[id_] |= die;
                    lootedStrength += drop;
                    p.strength -= drop;
                    aliveCount--;
                }

                // 分配给其他玩家
                int inc = lootedStrength / begin;
                for (i = 0; i < begin; i++) {
                    int id_ = containedPlayers[i];
                    Player p = players[id_];
                    bt.strengthDelta[id_] += inc;
                    p.strength += inc;
                }
            }
        }

        // 3. 产生豆子
        if (--generatorTurnLeft == 0) {
            generatorTurnLeft = GENERATOR_INTERVAL;
            NewFruits fruits = newFruits[newFruitsCount++];
            fruits.newFruitCount = 0;
            for (i = 0; i < generatorCount; i++)
                for (int d = up; d < 8; ++d) {
                    // 取余，穿过场地边界
                    int r = (generators[i].row + dy[d] + height) % height, c = (generators[i].col + dx[d] + width) % width;
                    if (((fieldStatic[r][c] & generator) != 0) || (fieldContent[r][c] & (smallFruit | largeFruit)) != 0)
                        continue;
                    fieldContent[r][c] |= smallFruit;
                    fruits.newFruits[fruits.newFruitCount].row = r;
                    fruits.newFruits[fruits.newFruitCount++].col = c;
                    smallFruitCount++;
                }
        }


        // 4. 吃掉豆子
        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p_ = players[id];
            if (p_.dead) continue;

            int content = fieldContent[p_.row][p_.col];

            // 只有在格子上只有自己的时候才能吃掉豆子
            if ((content & playerMask & ~playerID2Mask[id]) != 0) continue;

            if ((content & smallFruit) != 0) {
                fieldContent[p_.row][p_.col] &= ~smallFruit;
                p_.strength++;
                bt.strengthDelta[id]++;
                smallFruitCount--;
                bt.change[id] |= ateSmall;
            } else if ((content & largeFruit) != 0) {
                fieldContent[p_.row][p_.col] &= ~largeFruit;
                if (p_.powerUpLeft == 0) {
                    p_.strength += LARGE_FRUIT_ENHANCEMENT;
                    bt.strengthDelta[id] += LARGE_FRUIT_ENHANCEMENT;
                }
                p_.powerUpLeft += LARGE_FRUIT_DURATION;
                bt.change[id] |= ateLarge;
            }
        }

        // 5. 大豆回合减少
        for (id = 0; id < MAX_PLAYER_COUNT; id++) {
            Player p_ = players[id];
            if (p_.dead) continue;

            if (p_.powerUpLeft > 0 && --p_.powerUpLeft == 0) {
                p_.strength -= LARGE_FRUIT_ENHANCEMENT;
                bt.change[id] |= powerUpCancel;
                bt.strengthDelta[id] += -LARGE_FRUIT_ENHANCEMENT;
            }
        }


        ++turnID;

        // 是否只剩一人？
        if (aliveCount <= 1) {
            for (id = 0; id < MAX_PLAYER_COUNT; id++)
                if (!players[id].dead) {
                    bt.strengthDelta[id] += smallFruitCount;
                    players[id].strength += smallFruitCount;
                }
            return false;
        }

        // 是否回合超限？
        if (turnID >= 100) return false;

        return true;
    }

    // 读取输入
    int readInput() throws IOException, ParseException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder chunk = new StringBuilder();
        StringBuilder str = new StringBuilder();
        str = new StringBuilder(bufferedReader.readLine());
//            do {
//                chunk = new StringBuilder(bufferedReader.readLine());
//                str.append(chunk);
//            } while (chunk.length() > 0);
        JSONParser parser = new JSONParser();
        Object input = new JSONObject();
        try {
            input = parser.parse(str.toString());
        } catch (ParseException e) {
//            e.printStackTrace();
        }
        int len = ((JSONArray) JSONUtils.get(input, "requests")).size();
        Object field = (JSONUtils.get(input, new Object[]{"requests", 0}));
        Object staticField = (JSONUtils.get(field, "static"));
        Object contentField = (JSONUtils.get(field, "content"));
        height = Integer.parseInt(JSONUtils.get(field, "height").toString());
        width = Integer.parseInt(JSONUtils.get(field, "width").toString());
        LARGE_FRUIT_DURATION = Integer.parseInt(JSONUtils.get(field, "LARGE_FRUIT_DURATION").toString());
        LARGE_FRUIT_ENHANCEMENT = Integer.parseInt(JSONUtils.get(field, "LARGE_FRUIT_ENHANCEMENT").toString());
        generatorTurnLeft = GENERATOR_INTERVAL = Integer.parseInt(JSONUtils.get(field, "LARGE_FRUIT_ENHANCEMENT").toString());
        PrepareInitialField(staticField, contentField);
        for (int i = 1; i < len; i++) {
            Object req = JSONUtils.get(input, new Object[]{"requests", i});
            for (int id = 0; id < MAX_PLAYER_COUNT; id++) {
                if (!players[id].dead) {
                    actions[id] = Integer.parseInt(JSONUtils.get(req, new Object[]{playerID2str[id], "action"}).toString());
                }
            }
            nextTurn();
        }
        return Integer.parseInt(JSONUtils.get(field, "id").toString());
    }

    // 根据 static 和 content 数组准备场地的初始状况
    void PrepareInitialField(Object staticField, Object contentField) {
        int r, c, gid = 0;
        generatorCount = 0;
        aliveCount = 0;
        smallFruitCount = 0;
        generatorTurnLeft = GENERATOR_INTERVAL;


        for (r = 0; r < height; r++)
            for (c = 0; c < width; c++) {

                int content = fieldContent[r][c] = Integer.parseInt(JSONUtils.get(contentField, new Object[]{r, c}).toString());
                int s = fieldStatic[r][c] = Integer.parseInt(JSONUtils.get(staticField, new Object[]{r, c}).toString());
                if ((s & generator) != 0) {
                    generators[gid].row = r;
                    generators[gid++].col = c;
                    generatorCount++;
                }
                if ((content & smallFruit) != 0) smallFruitCount++;
                for (int id = 0; id < MAX_PLAYER_COUNT; id++)
                    if ((content & playerID2Mask[id]) != 0) {
                        Player p = players[id];
                        p.col = c;
                        p.row = r;
                        p.powerUpLeft = 0;
                        p.strength = 1;
                        p.dead = false;
                        aliveCount++;
                    }
            }
    }

    void writeOutput(int action) throws IOException {
        JSONObject ret = new JSONObject();
        JSONObject response = new JSONObject();
        response.put("action", action);
        ret.put("response", response);
        System.out.println(ret.toJSONString());
    }
}

class Helpers {
    final double[] actionScore = new double[5];
    private final Random rand = new Random();
    private static final int MAX_PLAYER_COUNT = 4; // 假设最大玩家数为4，根据实际情况调整

    public int RandBetween(int a, int b) {
        if (a > b) {
            int temp = a;
            a = b;
            b = temp;
        }
        return rand.nextInt(b - a) + a;
    }

    public void RandomPlay(GameField gameField, int myID) {
        int count = 0;
        int myAct = -1;
        while (true) {
            // 对每个玩家生成随机的合法动作
            for (int i = 0; i < MAX_PLAYER_COUNT; i++) {
                if (gameField.players[i].dead) {
                    continue;
                }
                int[] valid = new int[5];
                int vCount = 0;
                for (int d = GameField.stay; d < 4; ++d) {
                    if (gameField.ActionValid(i, d)) {
                        valid[vCount++] = d;
                    }
                }
                gameField.actions[i] = valid[RandBetween(0, vCount)];
            }

            if (count == 0) {
                myAct = gameField.actions[myID];
            }

            // 演算一步局面变化
            // NextTurn返回true表示游戏没有结束
            boolean hasNext = gameField.nextTurn();
            count++;

            if (!hasNext) {
                break;
            }
        }

        // 计算分数
        int total = 0;
        for (int i = 0; i < MAX_PLAYER_COUNT; i++) {
            total += gameField.players[i].strength;
        }
        actionScore[myAct + 1] += (10000.0 * gameField.players[myID].strength / total) / 100.0;

        // 恢复游戏状态到最初（就是本回合）
        for (int i = 0; i < count; i++) {
            gameField.popState();
        }
    }
}

class Main {
    public static void main(String[] args) throws IOException, ParseException {
        GameField gameField = new GameField();
        int myID = gameField.readInput();
        // 简单随机，看哪个动作随机赢得最多
        Helpers helpers = new Helpers();
        for (int i = 0; i < 3000; i++)
            helpers.RandomPlay(gameField, myID);

        int maxD = 0, d;
        for (d = 0; d < 5; d++)
            if (helpers.actionScore[d] > helpers.actionScore[maxD]) maxD = d;
        gameField.writeOutput((maxD - 1));
    }
}

class JSONUtils {
    public static Object get(Object json, String key) {
        if (json instanceof JSONObject) {
            return ((JSONObject) json).get(key);
        } else if (json instanceof JSONArray) {
            return get(json, Integer.parseInt(key));
        } else {
            return new JSONObject();
        }
    }

    public static Object get(Object json, Integer key) {
        if (json instanceof JSONArray) {
            return ((JSONArray) json).get(key);
        }
        return new JSONObject();
    }

    public static Object get(Object json, Object indices[]) {
        if (json == null) return null;
        if (indices.length == 1) {
            return get(json, indices[0].toString());
        } else if (indices.length > 1) {
            Object cpy[] = new Object[indices.length - 1];
            System.arraycopy(indices, 1, cpy, 0, indices.length - 1);
            if (indices[0] instanceof Integer) {
                return get(get(json, (Integer) indices[0]), cpy);
            } else {
                return get(get(json, indices[0].toString()), cpy);
            }
        } else {
            return json;
        }
    }
}





