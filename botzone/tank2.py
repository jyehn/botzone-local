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

def pacman():
    env_wrap = botzone.make('Pacman-wrap')
    from botzone.online.bot import BotConfig, Bot

    bot_1 = Bot(BotConfig.fromID("5756537a979f57127327b4a9"))
    bot_2 = Bot(BotConfig.fromID("5756537a979f57127327b4a9"))
    bot_3 = Bot(BotConfig.fromID("5756537a979f57127327b4a9"))
    bot_local = Bot(BotConfig(GameConfig.fromName("Pacman"), "./localcodes/pacman.java", "java"))
    runMatch(env_wrap, [bot_1, bot_2, bot_3, bot_local])


if __name__ == "__main__":
    pacman()
