#ifndef CONTROLLER_RL_H
#define CONTROLLER_RL_H

#include "controller_api.h"

// Backend de controlador para el agente de Reinforcement Learning.
// Consume los globals gRLButtons / gRLStickX / gRLStickY declarados en
// src/pc/rl_state.h y los inyecta en la OSContPad del frame actual.
// Solo actúa cuando gRLActive != 0.
extern struct ControllerAPI controller_rl;

#endif // CONTROLLER_RL_H
