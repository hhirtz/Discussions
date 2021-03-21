package site.srht.taiite.discussions.irc

const val RPL_WELCOME = "001"; // :Welcome message
const val RPL_YOURHOST = "002"; // :Your host is...
const val RPL_CREATED = "003"; // :This server was created...
const val RPL_MYINFO = "004"; // <servername> <version> <umodes> <chan modes> <chan modes with a parameter>
const val RPL_ISUPPORT = "005"; // 1*13<TOKEN[=value]> :are supported by this server

const val RPL_UMODEIS = "221"; // <modes>
const val RPL_LUSERCLIENT = "251"; // :<int> users and <int> services on <int> servers
const val RPL_LUSEROP = "252"; // <int> :operator(s) online
const val RPL_LUSERUNKNOWN = "253"; // <int> :unknown connection(s)
const val RPL_LUSERCHANNELS = "254"; // <int> :channels formed
const val RPL_LUSERME = "255"; // :I have <int> clients and <int> servers
const val RPL_ADMINME = "256"; // <server> :Admin info
const val RPL_ADMINLOC1 = "257"; // :<info>
const val RPL_ADMINLOC2 = "258"; // :<info>
const val RPL_ADMINMAIL = "259"; // :<info>

const val RPL_AWAY = "301"; // <nick> :<away message>
const val RPL_UNAWAY = "305"; // :You are no longer marked as being away
const val RPL_NOWAWAY = "306"; // :You have been marked as being away
const val RPL_WHOISUSER = "311"; // <nick> <user> <host> * :<realname>
const val RPL_WHOISSERVER = "312"; // <nick> <server> :<server info>
const val RPL_WHOISOPERATOR = "313"; // <nick> :is an IRC operator
const val RPL_ENDOFWHO = "315"; // <name> :End of WHO list
const val RPL_WHOISIDLE = "317"; // <nick> <integer> [<integer>] :seconds idle [, signon time]
const val RPL_ENDOFWHOIS = "318"; // <nick> :End of WHOIS list
const val RPL_WHOISCHANNELS = "319"; // <nick> :*( (@/+) <channel> " " )
const val RPL_LIST = "322"; // <channel> <# of visible members> <topic>
const val RPL_LISTEND = "323"; // :End of list
const val RPL_CHANNELMODEIS = "324"; // <channel> <modes> <mode params>
const val RPL_NOTOPIC = "331"; // <channel> :No topic set
const val RPL_TOPIC = "332"; // <channel> <topic>
const val RPL_TOPICWHOTIME = "333"; // <channel> <nick> <setat>
const val RPL_INVITING = "341"; // <nick> <channel>
const val RPL_INVITELIST = "346"; // <channel> <invite mask>
const val RPL_ENDOFINVITELIST = "347"; // <channel> :End of invite list
const val RPL_EXCEPTLIST = "348"; // <channel> <exception mask>
const val RPL_ENDOFEXCEPTLIST = "349"; // <channel> :End of exception list
const val RPL_VERSION = "351"; // <version> <servername> :<comments>
const val RPL_WHOREPLY = "352"; // <channel> <user> <host> <server> <nick> "H"/"G" ["*"] [("@"/"+")] :<hop count> <nick>
const val RPL_NAMREPLY = "353"; // <=/*/@> <channel> :1*(@/ /+user)
const val RPL_ENDOFNAMES = "366"; // <channel> :End of names list
const val RPL_BANLIST = "367"; // <channel> <ban mask>
const val RPL_ENDOFBANLIST = "368"; // <channel> :End of ban list
const val RPL_INFO = "371"; // :<info>
const val RPL_MOTD = "372"; // :- <text>
const val RPL_ENDOFINFO = "374"; // :End of INFO
const val RPL_MOTDSTART = "375"; // :- <servername> Message of the day -
const val RPL_ENDOFMOTD = "376"; // :End of MOTD command
const val RPL_YOUREOPER = "381"; // :You are now an operator
const val RPL_REHASHING = "382"; // <config file> :Rehashing
const val RPL_TIME = "391"; // <servername> :<time in whatever format>

const val ERR_NOSUCHNICK = "401"; // <nick> :No such nick/channel
const val ERR_NOSUCHCHANNEL = "403"; // <channel> :No such channel
const val ERR_CANNOTSENDTOCHAN = "404"; // <channel> :Cannot send to channel
const val ERR_INVALIDCAPCMD = "410"; // <command> :Unknown cap command
const val ERR_NORECIPIENT = "411"; // :No recipient given
const val ERR_NOTEXTTOSEND = "412"; // :No text to send
const val ERR_INPUTTOOLONG = "417"; // :Input line was too long
const val ERR_UNKNOWNCOMMAND = "421"; // <command> :Unknown command
const val ERR_NOMOTD = "422"; // :MOTD file missing
const val ERR_NONICKNAMEGIVEN = "431"; // :No nickname given
const val ERR_ERRONEUSNICKNAME = "432"; // <nick> :Erroneous nickname
const val ERR_NICKNAMEINUSE = "433"; // <nick> :Nickname in use
const val ERR_USERNOTINCHANNEL = "441"; // <nick> <channel> :User not in channel
const val ERR_NOTONCHANNEL = "442"; // <channel> :You're not on that channel
const val ERR_USERONCHANNEL = "443"; // <user> <channel> :is already on channel
const val ERR_NOTREGISTERED = "451"; // :You have not registered
const val ERR_NEEDMOREPARAMS = "461"; // <command> :Not enough parameters
const val ERR_ALREADYREGISTRED = "462"; // :Already registered
const val ERR_PASSWDMISMATCH = "464"; // :Password incorrect
const val ERR_YOUREBANNEDCREEP = "465"; // :You're banned from this server
const val ERR_KEYSET = "467"; // <channel> :Channel key already set
const val ERR_CHANNELISFULL = "471"; // <channel> :Cannot join channel (+l)
const val ERR_UNKNOWNMODE = "472"; // <char> :Don't know this mode for <channel>
const val ERR_INVITEONLYCHAN = "473"; // <channel> :Cannot join channel (+I)
const val ERR_BANNEDFROMCHAN = "474"; // <channel> :Cannot join channel (+b)
const val ERR_BADCHANKEY = "475"; // <channel> :Cannot join channel (+k)
const val ERR_NOPRIVILEDGES = "481"; // :Permission Denied- You're not an IRC operator
const val ERR_CHANOPRIVSNEEDED = "482"; // <channel> :You're not an operator

const val ERR_UMODEUNKNOWNFLAG = "501"; // :Unknown mode flag
const val ERR_USERSDONTMATCH = "502"; // :Can't change mode for other users

const val RPL_LOGGEDIN = "900"; // <nick> <nick>!<ident>@<host> <account> :You are now logged in as <user>
const val RPL_LOGGEDOUT = "901"; // <nick> <nick>!<ident>@<host> :You are now logged out
const val ERR_NICKLOCKED = "902"; // :You must use a nick assigned to you
const val RPL_SASLSUCCESS = "903"; // :SASL authentication successful
const val ERR_SASLFAIL = "904"; // :SASL authentication failed
const val ERR_SASLTOOLONG = "905"; // :SASL message too long
const val ERR_SASLABORTED = "906"; // :SASL authentication aborted
const val ERR_SASLALREADY = "907"; // :You have already authenticated using SASL
const val RPL_SASLMECHS = "908"; // <mechanisms> :are available SASL mechanisms
