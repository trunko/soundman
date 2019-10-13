package SoundMan

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.core.AccountType
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.core.entities.VoiceChannel
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import net.dv8tion.jda.core.managers.AudioManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class App extends ListenerAdapter {
    private static final Set<String> FILE_TYPES = Set.of("mp3", "wav")
    private static final Logger LOGGER = LoggerFactory.getLogger("SoundMan")

    private static String token
    private static String prefix
    private static String path
    private static String link

    static void main(String[] args) {
        try {
            InputStream config = App.class.getResourceAsStream("/config.properties")
            Properties prop = new Properties()

            prop.load(config)

            token = prop.getProperty("token")
            prefix = prop.getProperty("prefix")
            path = prop.getProperty("path")
            link = prop.getProperty("link")
        } catch (IOException ex) {
            LOGGER.error("Error reading configuration file.", ex)
            println "Error reading configuration file."
            return
        }

        try {
            JDABuilder bot = new JDABuilder(AccountType.BOT)
            bot.setToken(token)
            bot.addEventListener(new App())
            bot.build()

            println "Successfully logged in!"
            LOGGER.info("Successfully logged in!")
        } catch (Exception ex) {
            LOGGER.error("Error while executing bot initialization.", ex)
        }
    }

    private final AudioPlayerManager playerManager
    private final Map<Long, GuildMusicManager> musicManagers

    private App() {
        this.musicManagers = new HashMap<>()

        this.playerManager = new DefaultAudioPlayerManager()
        AudioSourceManagers.registerRemoteSources(playerManager)
        AudioSourceManagers.registerLocalSource(playerManager)
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId())
        GuildMusicManager musicManager = musicManagers.get(guildId)

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager)
            musicManagers.put(guildId, musicManager)
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler())

        return musicManager
    }

    @Override
    void onMessageReceived(MessageReceivedEvent event) {
        String command = event.getMessage().getContentRaw()
        boolean helpList = false

        if (!command.startsWith(prefix)) {
            return
        }

        command = command.substring(prefix.length())

        if (command == "list" || command == "help") {
            String commandList = "Commands:\n" + "```\n" + prefix + "{sound_name}\n" + prefix + "{url}\n" + prefix
            +"random\n" + prefix + "volume\n" + prefix + "skip\n" + "```\n"

            if (link != null && !link.isEmpty()) {
                commandList = commandList.concat("Sound List: $link")
            }

            LOGGER.info("Sending help list to ${event.getAuthor().getName()}")

            String finalCommandList = commandList
            event.getAuthor().openPrivateChannel().queue({ channel -> channel.sendMessage(finalCommandList).queue() })

            helpList = true
        }

        Guild guild = event.getGuild()

        if (guild != null) {
            if (command == "skip") {
                println "\nSkipping to next track."
                LOGGER.info("Skipping to next track.")
                skipTrack(event.getTextChannel())
            } else if (command.startsWith("https://") || command.startsWith("http://")) {
                println "\nSound URL requested."
                LOGGER.info("Sound URL requested.")
                loadAndPlay(event.getTextChannel(), command, false)
            } else if (command.startsWith("random")) {
                try {
                    String sound = getRandomSoundPath()
                    println "\nRandom sound requested: ${sound.substring(path.length()).split("\\.")[0]}"
                    LOGGER.info("Random sound requested: {}", sound.substring(path.length()).split("\\.")[0])
                    loadAndPlay(event.getTextChannel(), sound, true)
                } catch (Exception e) {
                    LOGGER.error(e.getMessage())
                }
            } else if (command.startsWith("volume")) {
                try {
                    int val = Integer.parseInt(command.split(" ")[1])
                    println "\nVolume: " + val
                    LOGGER.info("Volume: $val")
                    volume(event.getTextChannel(), val)
                } catch (NumberFormatException ignored) {
                    LOGGER.error("Unable to parse '{}' as an integer to set the volume as.", command.split(" ")[1])
                }
            } else if (!helpList) {
                println "\nSound requested: $command"
                LOGGER.info("Sound requested: $command")
                try {
                    loadAndPlay(event.getTextChannel(), getFilePath(command), true)
                } catch (Exception e) {
                    LOGGER.error(e.getMessage())
                }
            }

            if (!helpList) {
                println "Requested by: ${event.getAuthor().getName()}"
                LOGGER.info("Requested by: {}", event.getAuthor().getName())
            }
        }

        event.getMessage().delete().queue()

        super.onMessageReceived(event)
    }

    private static String getRandomSoundPath() {
        File[] files = new File(path).listFiles()
        List<String> sounds = new ArrayList<>()

        if (files == null) {
            throw new Exception("No files were found in the directory: " + path)
        }

        files.each {
            if (it.isFile() && FILE_TYPES.contains(it.getName().split("\\.")[1])) {
                sounds.add(it.getName())
            }
        }

        if (sounds.size() < 1) {
            throw new Exception("No sound files with an appropriate file type were found in the directory: " + path)
        }

        Random rand = new Random()
        String selected = sounds.get(rand.nextInt(sounds.size()))

        return path + selected
    }

    private static String getFilePath(String fileName) {
        File[] files = new File(path).listFiles()

        if (files == null) {
            throw new Exception("No files were found in the directory: " + path)
        }

        for (File file : files) {
            String[] fileInfo = file.getName().split("\\.")
            if (file.isFile() && fileInfo[0] == fileName && FILE_TYPES.contains(fileInfo[1])) {
                fileName = file.getName()
            }
        }

        return path + fileName
    }

    private void volume(final TextChannel channel, int val) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild())
        if (val > 100)
            val = 100

        musicManager.player.setVolume(val)
    }

    private void loadAndPlay(final TextChannel channel, final String trackUrl, boolean local) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild())

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            void trackLoaded(AudioTrack track) {
                if (!local) {
                    LOGGER.info("Adding to queue: {}", track.getInfo().title)
                    println "Adding to queue: ${track.getInfo().title}"
                }

                play(channel.getGuild(), musicManager, track)
            }

            @Override
            void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack()

                if (firstTrack == null)
                    firstTrack = playlist.getTracks().get(0)

                LOGGER.info("Adding to queue ${firstTrack.getInfo().title} (first track of playlist ${playlist.getName()})")
                println "Adding to queue ${firstTrack.getInfo().title} (first track of playlist ${playlist.getName()})"

                play(channel.getGuild(), musicManager, firstTrack)
            }

            @Override
            void noMatches() {
                if (!local) {
                    println "Invalid link: $trackUrl"
                    LOGGER.info("Invalid link: {}", trackUrl)
                } else {
                    println "Invalid sound file: ${trackUrl.substring(path.length()).split("\\.")[0]}"
                    LOGGER.info("Invalid sound file: {}", trackUrl.substring(path.length()).split("\\.")[0])
                }
            }

            @Override
            void loadFailed(FriendlyException exception) {
                println "Error loading sound: ${exception.getMessage()}"
                LOGGER.info("Error loading sound: {}", exception.getMessage())
            }
        })
    }

    private static void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager())

        musicManager.scheduler.queue(track)
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild())
        musicManager.scheduler.nextTrack()
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel in audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel)
                break
            }
        }
    }
}
