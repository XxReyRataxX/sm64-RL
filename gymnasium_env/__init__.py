from gymnasium.envs.registration import register

register(
    id="gymnasium_env/Killer-v0",
    entry_point="gymnasium_env.envs:SM64KillerEnv",
)
