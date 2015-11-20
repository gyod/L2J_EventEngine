/*
 * Copyright (C) 2015-2015 L2J EventEngine
 *
 * This file is part of L2J EventEngine.
 *
 * L2J EventEngine is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * L2J EventEngine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.eventengine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.l2jserver.gameserver.ThreadPoolManager;
import com.l2jserver.gameserver.instancemanager.InstanceManager;
import com.l2jserver.gameserver.model.actor.L2Playable;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.events.EventType;
import com.l2jserver.gameserver.model.events.ListenerRegisterType;
import com.l2jserver.gameserver.model.events.annotations.Priority;
import com.l2jserver.gameserver.model.events.annotations.RegisterEvent;
import com.l2jserver.gameserver.model.events.annotations.RegisterType;
import com.l2jserver.gameserver.model.events.impl.character.OnCreatureAttack;
import com.l2jserver.gameserver.model.events.impl.character.OnCreatureKill;
import com.l2jserver.gameserver.model.events.impl.character.OnCreatureSkillUse;
import com.l2jserver.gameserver.model.events.impl.character.npc.OnNpcFirstTalk;
import com.l2jserver.gameserver.model.events.impl.character.player.OnPlayerEquipItem;
import com.l2jserver.gameserver.model.events.impl.character.player.OnPlayerLogin;
import com.l2jserver.gameserver.model.events.impl.character.player.OnPlayerLogout;
import com.l2jserver.gameserver.model.events.returns.TerminateReturn;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.network.clientpackets.Say2;
import com.l2jserver.gameserver.network.serverpackets.CreatureSay;
import com.l2jserver.util.Rnd;

import net.sf.eventengine.ai.NpcManager;
import net.sf.eventengine.datatables.BuffListData;
import net.sf.eventengine.datatables.ConfigData;
import net.sf.eventengine.datatables.EventData;
import net.sf.eventengine.datatables.MessageData;
import net.sf.eventengine.enums.EventEngineState;
import net.sf.eventengine.events.handler.AbstractEvent;
import net.sf.eventengine.events.holders.PlayerHolder;
import net.sf.eventengine.task.EventEngineTask;

/**
 * @author fissban
 */
public class EventEngineManager extends Quest
{
	private static final Logger LOGGER = Logger.getLogger(EventEngineManager.class.getName());
	
	/**
	 * Constructor
	 */
	public EventEngineManager()
	{
		super(-1, EventEngineManager.class.getSimpleName(), "EventEngineManager");
		
		load();
	}
	
	/**
	 * It loads all the dependencies needed by EventEngine
	 */
	private void load()
	{
		try
		{
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Adapter loaded.");
			// Load event configs
			ConfigData.getInstance();
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Configs loaded");
			EventData.getInstance();
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Events loaded");
			initVotes();
			// Load buff list
			BuffListData.getInstance();
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Buffs loaded.");
			// Load Multi-Language System
			MessageData.getInstance();
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Multi-Language system loaded.");
			// Load Npc Manager
			NpcManager.class.newInstance();
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": AI's loaded.");
			// Launch main timer
			_time = 0;
			ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new EventEngineTask(), 10 * 1000, 1000);
			LOGGER.info(EventEngineManager.class.getSimpleName() + ": Timer loaded.");
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> load() " + e);
			e.printStackTrace();
		}
	}
	
	// XXX EventEngineTask ------------------------------------------------------------------------------------
	private int _time;
	
	public int getTime()
	{
		return _time;
	}
	
	public void setTime(int time)
	{
		_time = time;
	}
	
	public void decreaseTime()
	{
		_time--;
	}
	
	// XXX NEXT EVENT ---------------------------------------------------------------------------------
	
	private Class<? extends AbstractEvent> _nextEvent;
	
	/**
	 * Get the next event type
	 * @return
	 */
	public Class<? extends AbstractEvent> getNextEvent()
	{
		return _nextEvent;
	}
	
	/**
	 * Set the next event type
	 * @param event
	 */
	public void setNextEvent(Class<? extends AbstractEvent> event)
	{
		_nextEvent = event;
	}
	
	// XXX CURRENT EVENT ---------------------------------------------------------------------------------
	
	// Evento que esta corriendo.
	private AbstractEvent _currentEvent;
	
	/**
	 * Obtenemos el evento q esta corriendo actualmente.
	 * @return
	 */
	public AbstractEvent getCurrentEvent()
	{
		return _currentEvent;
	}
	
	/**
	 * Definimos el evento q comenzara a correr.
	 * @param event
	 */
	public void setCurrentEvent(AbstractEvent event)
	{
		_currentEvent = event;
	}
	
	// XXX LISTENERS -------------------------------------------------------------------------------------
	// When a playable attack a character
	@RegisterEvent(EventType.ON_CREATURE_ATTACK)
	@RegisterType(ListenerRegisterType.GLOBAL)
	@Priority(Integer.MAX_VALUE)
	public TerminateReturn onPlayableAttack(OnCreatureAttack event)
	{
		if (_currentEvent == null)
		{
			return null;
		}
		
		try
		{
			if ((event.getAttacker() == null) || !event.getAttacker().isPlayable())
			{
				return null;
			}
			
			if (_currentEvent.listenerOnAttack((L2Playable) event.getAttacker(), event.getTarget()))
			{
				return new TerminateReturn(true, true, true);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnAttack() " + e);
			e.printStackTrace();
		}
		
		return null;
	}
	
	// When a playable uses a skill
	@RegisterEvent(EventType.ON_CREATURE_SKILL_USE)
	@RegisterType(ListenerRegisterType.GLOBAL)
	@Priority(Integer.MAX_VALUE)
	public TerminateReturn onPlayableUseSkill(OnCreatureSkillUse event)
	{
		// Si no se esta corriendo no continuar el listener.
		if (_currentEvent == null)
		{
			return null;
		}
		
		try
		{
			if (!event.getCaster().isPlayable())
			{
				return null;
			}
			
			if (_currentEvent.listenerOnUseSkill((L2Playable) event.getCaster(), event.getTarget(), event.getSkill()))
			{
				return new TerminateReturn(true, true, true);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnUseSkill() " + e);
			e.printStackTrace();
		}
		
		return null;
		
	}
	
	// When a playable kills a character and a player dies
	@RegisterEvent(EventType.ON_CREATURE_KILL)
	@RegisterType(ListenerRegisterType.GLOBAL)
	@Priority(Integer.MAX_VALUE)
	public TerminateReturn onCharacterKill(OnCreatureKill event)
	{
		if (_currentEvent == null)
		{
			return null;
		}
		
		try
		{
			if ((event.getAttacker() != null) && event.getAttacker().isPlayable())
			{
				_currentEvent.listenerOnKill((L2Playable) event.getAttacker(), event.getTarget());
			}
			
			if (event.getTarget().isPlayer())
			{
				_currentEvent.listenerOnDeath((L2PcInstance) event.getTarget());
			}
			
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnKill() " + e);
			e.printStackTrace();
		}
		
		return null;
	}
	
	// When a player talks with npc
	@RegisterEvent(EventType.ON_NPC_FIRST_TALK)
	@RegisterType(ListenerRegisterType.GLOBAL_NPCS)
	@Priority(Integer.MAX_VALUE)
	public void onNpcInteract(OnNpcFirstTalk event)
	{
		if (_currentEvent == null)
		{
			return;
		}
		
		try
		{
			_currentEvent.listenerOnInteract(event.getActiveChar(), event.getNpc());
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnInteract() " + e);
			e.printStackTrace();
		}
		
		return;
	}
	
	// When the player exits
	@RegisterEvent(EventType.ON_PLAYER_LOGOUT)
	@RegisterType(ListenerRegisterType.GLOBAL_PLAYERS)
	@Priority(Integer.MAX_VALUE)
	public void onPlayerLogout(OnPlayerLogout event)
	{
		// Si no se esta corriendo no continuar el listener.
		if (_currentEvent == null)
		{
			if (_state == EventEngineState.REGISTER || _state == EventEngineState.VOTING)
			{
				removeVote(event.getActiveChar());
				unRegisterPlayer(event.getActiveChar());
			}
			
			return;
		}
		
		if (_currentEvent.getPlayerEventManager().isPlayableInEvent(event.getActiveChar()))
		{
			try
			{
				PlayerHolder ph = _currentEvent.getPlayerEventManager().getEventPlayer(event.getActiveChar());
				// recobramos el color del titulo original
				ph.recoverOriginalColorTitle();
				// recobramos el titulo original
				ph.recoverOriginalTitle();
				// remobemos al personaje del mundo creado
				InstanceManager.getInstance().getWorld(ph.getDinamicInstanceId()).removeAllowed(ph.getPcInstance().getObjectId());
				
				_currentEvent.getPlayerEventManager().getAllEventPlayers().remove(ph);
			}
			catch (Exception e)
			{
				LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnLogout() " + e);
				e.printStackTrace();
			}
		}
	}
	
	// When the player logins
	@RegisterEvent(EventType.ON_PLAYER_LOGIN)
	@RegisterType(ListenerRegisterType.GLOBAL_PLAYERS)
	@Priority(Integer.MAX_VALUE)
	public void onPlayerLogin(OnPlayerLogin event)
	{
		event.getActiveChar().sendPacket(new CreatureSay(0, Say2.PARTYROOM_COMMANDER, "", MessageData.getInstance().getMsgByLang(event.getActiveChar(), "event_login_participate", true)));
		event.getActiveChar().sendPacket(new CreatureSay(0, Say2.PARTYROOM_COMMANDER, "", MessageData.getInstance().getMsgByLang(event.getActiveChar(), "event_login_vote", true)));
	}
	
	// When a player equips an item
	@RegisterEvent(EventType.ON_PLAYER_EQUIP_ITEM)
	@RegisterType(ListenerRegisterType.GLOBAL_PLAYERS)
	@Priority(Integer.MAX_VALUE)
	public TerminateReturn onUseItem(OnPlayerEquipItem event)
	{
		// Si no se esta corriendo no continuar el listener.
		if (_currentEvent == null)
		{
			return null;
		}
		
		try
		{
			if (_currentEvent.listenerOnUseItem(event.getActiveChar(), event.getItem().getItem()))
			{
				return new TerminateReturn(true, true, true);
			}
		}
		catch (Exception e)
		{
			LOGGER.warning(EventEngineManager.class.getSimpleName() + ": -> listenerOnUseItem() " + e);
			e.printStackTrace();
		}
		return null;
	}
	
	// XXX EVENT VOTE ------------------------------------------------------------------------------------
	
	// Lista de id's de personajes que votaron
	private final Set<Integer> _playersAlreadyVoted = ConcurrentHashMap.newKeySet();
	// Mapa de con los id's de los personajes que los votaron
	private final Map<Class<? extends AbstractEvent>, Set<Integer>> _currentEventVotes = new HashMap<>();
	
	/**
	 * Init votes
	 */
	public void initVotes()
	{
		for (Class<? extends AbstractEvent> type : EventData.getInstance().getEnabledEvents())
		{
			_currentEventVotes.put(type, ConcurrentHashMap.newKeySet());
		}
	}
	
	/**
	 * Clase encargada de inicializar los votos de cada evento.
	 * @return Map<EventType, Integer>
	 */
	public void clearVotes()
	{
		// Se reinicia el mapa
		for (Class<? extends AbstractEvent> event : _currentEventVotes.keySet())
		{
			_currentEventVotes.get(event).clear();
		}
		// Se limpia la lista de jugadores que votaron
		_playersAlreadyVoted.clear();
	}
	
	/**
	 * Incrementamos en uno la cantidad de votos
	 * @param player -> personaje q esta votando
	 * @param event -> evento al q se vota
	 * @return boolean
	 */
	public void increaseVote(L2PcInstance player, Class<? extends AbstractEvent> event)
	{
		// Agrega al personaje a la lista de los que votaron
		// Si ya estaba, sigue de largo
		// Sino, agrega un voto al evento
		if (_playersAlreadyVoted.add(player.getObjectId()))
		{
			_currentEventVotes.get(event).add(player.getObjectId());
		}
	}
	
	/**
	 * Disminuímos la cantidad de votos
	 * @param player -> personaje q esta votando
	 * @return
	 */
	public void removeVote(L2PcInstance player)
	{
		// Lo borra de la lista de jugadores que votaron
		if (_playersAlreadyVoted.remove(player.getObjectId()))
		{
			// Si estaba en la lista, empieza a buscar para qué evento votó
			for (Class<? extends AbstractEvent> event : _currentEventVotes.keySet())
			{
				_currentEventVotes.get(event).remove(player.getObjectId());
			}
		}
	}
	
	/**
	 * Obtenemos la cantidad de votos q tiene un determinado evento.
	 * @param event -> AVA, TVT, CFT.
	 * @return int
	 */
	public int getCurrentVotesInEvent(Class<? extends AbstractEvent> event)
	{
		return _currentEventVotes.get(event).size();
	}
	
	/**
	 * Obtenemos la cantidad de votos totales.
	 * @return
	 */
	public int getAllCurrentVotesInEvents()
	{
		int count = 0;
		for (Set<Integer> set : _currentEventVotes.values())
		{
			count += set.size();
		}
		
		return count;
	}
	
	/**
	 * Obtenemos el evento con mayor votos<br>
	 * En caso de tener todos la misma cant de votos se hace un random<br>
	 * entre los que más votos tienen<br>
	 * @return
	 */
	public Class<? extends AbstractEvent> getEventMoreVotes()
	{
		int maxVotes = 0;
		List<Class<? extends AbstractEvent>> topEvents = new ArrayList<>();
		
		for (Class<? extends AbstractEvent> event : _currentEventVotes.keySet())
		{
			int eventVotes = _currentEventVotes.get(event).size();
			if (eventVotes > maxVotes)
			{
				topEvents.clear();
				topEvents.add(event);
				maxVotes = eventVotes;
			}
			else if (eventVotes == maxVotes)
			{
				topEvents.add(event);
			}
		}
		
		int topEventsSize = topEvents.size();
		if (topEventsSize > 1)
		{
			return topEvents.get(Rnd.get(0, topEventsSize - 1));
		}
		
		return topEvents.get(0);
	}
	
	// XXX EVENT STATE -----------------------------------------------------------------------------------
	
	// variable encargada de controlar en que momento se podran registrar los usuarios a los eventos.
	private EventEngineState _state = EventEngineState.WAITING;
	
	/**
	 * Revisamos en q estado se encuentra el engine
	 * @return EventState
	 */
	public EventEngineState getEventEngineState()
	{
		return _state;
	}
	
	/**
	 * Definimos el estado en q se encuentra el evento<br>
	 * <u>Observaciones:</u><br>
	 * <li>REGISTER -> Indicamos q se esta</li><br>
	 * @param state
	 */
	public void setEventEngineState(EventEngineState state)
	{
		_state = state;
	}
	
	/**
	 * Get if the EventEngine is waiting to start a register or voting time.
	 * @return boolean
	 */
	public boolean isWaiting()
	{
		return _state == EventEngineState.WAITING;
	}
	
	/**
	 * Get if the EventEngine is running an event.
	 * @return boolean
	 */
	public boolean isRunning()
	{
		return (_state == EventEngineState.RUNNING_EVENT) || (_state == EventEngineState.RUN_EVENT);
	}
	
	/**
	 * Verificamos si se pueden seguir registrando mas usuarios a los eventos.
	 * @return boolean
	 */
	public boolean isOpenRegister()
	{
		return _state == EventEngineState.REGISTER;
	}
	
	/**
	 * Verificamos si se pueden seguir registrando mas usuarios a los eventos.
	 * @return boolean
	 */
	public boolean isOpenVote()
	{
		return _state == EventEngineState.VOTING;
	}
	
	// XXX PLAYERS REGISTER -----------------------------------------------------------------------------
	
	// Lista de players en el evento.
	private final Set<L2PcInstance> _eventRegisterdPlayers = ConcurrentHashMap.newKeySet();
	
	/**
	 * Obtenemos la colección de jugadores registrados
	 * @return Collection<L2PcInstance>
	 */
	public Collection<L2PcInstance> getAllRegisteredPlayers()
	{
		return _eventRegisterdPlayers;
	}
	
	/**
	 * Limpia la colección de jugadores
	 * @return
	 */
	public void clearRegisteredPlayers()
	{
		_eventRegisterdPlayers.clear();
	}
	
	/**
	 * Obtenemos si la cantidad de jugadores registrados es 0
	 * @return
	 * 		<li>True - > no hay jugadores registrados.</li><br>
	 *         <li>False - > hay al menos un jugador registrado.</li><br>
	 */
	public boolean isEmptyRegisteredPlayers()
	{
		return _eventRegisterdPlayers.isEmpty();
	}
	
	/**
	 * Obtenemos si el jugador se encuentra registrado
	 * @return
	 * 		<li>True - > Está registrado.</li><br>
	 *         <li>False - > No está registrado.</li><br>
	 */
	public boolean isRegistered(L2PcInstance player)
	{
		return _eventRegisterdPlayers.contains(player);
	}
	
	/**
	 * Agregamos un player al registro
	 * @param player
	 * @return
	 * 		<li>True - > si el registro es exitoso.</li><br>
	 *         <li>False - > si el player ya estaba registrado.</li><br>
	 */
	public boolean registerPlayer(L2PcInstance player)
	{
		return _eventRegisterdPlayers.add(player);
	}
	
	/**
	 * Eliminamos un player del registro
	 * @param player
	 * @return
	 * 		<li>True - > si el player estaba registrado.</li><br>
	 *         <li>False - > si el player no estaba registrado.</li><br>
	 */
	public boolean unRegisterPlayer(L2PcInstance player)
	{
		return _eventRegisterdPlayers.remove(player);
	}
	
	// XXX MISC ---------------------------------------------------------------------------------------
	/**
	 * Cleanup variables to the next event
	 */
	public void cleanUp()
	{
		setCurrentEvent(null);
		clearVotes();
		clearRegisteredPlayers();
	}
	
	/**
	 * Verificamos si un player participa de algun evento
	 * @param player
	 * @return
	 */
	public boolean isPlayerInEvent(L2PcInstance player)
	{
		if (_currentEvent == null)
		{
			return false;
		}
		
		return _currentEvent.getPlayerEventManager().isPlayableInEvent(player);
	}
	
	/**
	 * Verificamos si un playable participa de algun evento
	 * @param playable
	 * @return
	 */
	public boolean isPlayableInEvent(L2Playable playable)
	{
		if (_currentEvent == null)
		{
			return false;
		}
		
		return _currentEvent.getPlayerEventManager().isPlayableInEvent(playable);
	}
	
	public static EventEngineManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final EventEngineManager _instance = new EventEngineManager();
	}
}
