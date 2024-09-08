import botzone
from botzone.online.game import GameConfig
from botzone.online import compiler


def runMatch(env, bots):
    env.init(bots)
    score = env.reset()
    env.render()
    while score is None:
        score = env.step()
        env.render()
    else:
        print('Score:', score)


def tank1v1():
    env_wrap = botzone.make('Tank2-wrap')

    from botzone.online.bot import BotConfig, Bot

    bot_rabbit = Bot(BotConfig.fromID('5cee8d18641dd10fdcc92468'))  # rabbit
    bot_aky = Bot(BotConfig.fromID('5ce4b80fd2337e01c7a6728a'))  #
    bot_local = Bot(BotConfig(GameConfig.fromName("Tank2"), "./localcodes/Tank2_Random.cpp17", "cpp17"))
    runMatch(env_wrap, [bot_local, bot_rabbit])


if __name__ == "__main__":
    tank1v1()
