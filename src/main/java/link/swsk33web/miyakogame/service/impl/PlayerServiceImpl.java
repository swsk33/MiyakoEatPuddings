package link.swsk33web.miyakogame.service.impl;

import link.swsk33web.miyakogame.dao.PlayerDAO;
import link.swsk33web.miyakogame.dataobject.Player;
import link.swsk33web.miyakogame.model.RankInfo;
import link.swsk33web.miyakogame.model.Result;
import link.swsk33web.miyakogame.param.CommonValue;
import link.swsk33web.miyakogame.service.PlayerService;
import link.swsk33web.miyakogame.util.PwdUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"rawtypes", "unchecked"})
@Component
public class PlayerServiceImpl implements PlayerService {

	@Autowired
	private PlayerDAO playerDAO;

	@Autowired
	private RedisTemplate<Object, Object> redisTemplate;

	@Autowired
	private JavaMailSender mailSender;

	@Value("${spring.mail.username}")
	private String sender;

	/**
	 * 发送通知邮件
	 *
	 * @param email 收件人
	 * @param title 邮件标题
	 * @param text  邮件正文
	 */
	private void sendNotifyMail(String email, String title, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(sender);
		message.setTo(email);
		message.setSubject(title);
		message.setText(text);
		try {
			mailSender.send(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Result<Player> register(Player player) {
		Result<Player> result = new Result<>();
		if (player == null) {
			result.setResultFailed("数据体不能为空！");
			return result;
		}
		if (StringUtils.isEmpty(player.getUserName())) {
			result.setResultFailed("用户名不能为空！");
			return result;
		}
		if (StringUtils.isEmpty(player.getPwd())) {
			result.setResultFailed("密码不能为空！");
			return result;
		}
		if (player.getPwd().length() < 8) {
			result.setResultFailed("密码长度不能小于8！");
			return result;
		}
		if (StringUtils.isEmpty(player.getEmail())) {
			result.setResultFailed("邮箱不能为空！");
			return result;
		}
		if (!player.getEmail().contains("@")) {
			result.setResultFailed("邮箱格式不正确！");
			return result;
		}
		// 先从Redis找这个用户
		Integer getId = (Integer) redisTemplate.opsForHash().get(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, player.getUserName());
		if (getId == null) {
			Player getPlayer = null;
			// Redis找不着，再去MySQL中找
			try {
				getPlayer = playerDAO.findByUserName(player.getUserName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (getPlayer != null) {
				result.setResultFailed("该用户名已存在！");
				redisTemplate.opsForValue().set(getPlayer.getId(), getPlayer);
				redisTemplate.opsForHash().put(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, getPlayer.getUserName(), getPlayer.getId());
				return result;
			}
		} else {
			result.setResultFailed("该用户名已存在！");
			return result;
		}
		if (StringUtils.isEmpty(player.getAvatar())) {
			player.setAvatar("/avatars/default-" + (new Random().nextInt(5) + 1) + ".jpg");
		}
		if (StringUtils.isEmpty(player.getNickname())) {
			player.setNickname(player.getUserName());
		}
		player.setPwd(PwdUtils.encodePwd(player.getPwd()));
		player.setHighScore(0);
		player.setGameData("null");
		player.setGmtCreated(LocalDateTime.now());
		player.setGmtModified(LocalDateTime.now());
		playerDAO.add(player);
		redisTemplate.opsForValue().set(player.getId(), player);
		redisTemplate.opsForHash().put(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, player.getUserName(), player.getId());
		// 加入Redis排名表
		redisTemplate.opsForZSet().add(CommonValue.REDIS_RANK_TABLE_NAME, player.getId(), player.getHighScore());
		// 如果这个新注册的名字在无效用户名集合中则去掉
		if (redisTemplate.opsForSet().isMember(CommonValue.REDIS_INVALID_USER_TABLE_NAME, player.getUserName())) {
			redisTemplate.opsForSet().remove(CommonValue.REDIS_INVALID_USER_TABLE_NAME, player.getUserName());
		}
		result.setResultSuccess("注册用户成功！", player);
		return result;
	}

	@Override
	public Result<Player> login(Player player) {
		Result<Player> result = new Result<>();
		if (player == null) {
			result.setResultFailed("数据体不能为空！");
			return result;
		}
		if (StringUtils.isEmpty(player.getUserName())) {
			result.setResultFailed("用户名不能为空！");
			return result;
		}
		if (StringUtils.isEmpty(player.getPwd())) {
			result.setResultFailed("密码不能为空！");
			return result;
		}
		// 检测无效用户名防止缓存穿透
		if (redisTemplate.opsForSet().isMember(CommonValue.REDIS_INVALID_USER_TABLE_NAME, player.getUserName())) {
			result.setResultFailed("请勿重复登录无效账户！");
			return result;
		}
		// 先去Redis查询，没有再去数据库
		Integer getId = (Integer) redisTemplate.opsForHash().get(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, player.getUserName());
		Player getPlayer = null;
		if (getId != null) {
			getPlayer = (Player) redisTemplate.opsForValue().get(getId);
		} else {
			try {
				getPlayer = playerDAO.findByUserName(player.getUserName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (getPlayer == null) {
				result.setResultFailed("用户不存在！");
				// 无效的用户名存入Redis的无效用户名集合中，防止缓存穿透
				redisTemplate.opsForSet().add(CommonValue.REDIS_INVALID_USER_TABLE_NAME, player.getUserName());
				redisTemplate.expire(CommonValue.REDIS_INVALID_USER_TABLE_NAME, 1200, TimeUnit.SECONDS);
				return result;
			} else {
				redisTemplate.opsForValue().set(player.getId(), getPlayer);
				redisTemplate.opsForHash().put(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, getPlayer.getUserName(), getPlayer.getId());
			}
		}
		if (!PwdUtils.encodePwd(player.getPwd()).equals(getPlayer.getPwd())) {
			result.setResultFailed("用户名或者密码错误！");
			return result;
		}
		result.setResultSuccess("登录成功", getPlayer);
		return result;
	}

	@Override
	public Result<Player> delete(int id) {
		Result<Player> result = new Result<>();
		Player getPlayer = (Player) redisTemplate.opsForValue().get(id);
		if (getPlayer == null) {
			try {
				getPlayer = playerDAO.findById(id);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (getPlayer == null) {
				result.setResultFailed("找不到指定用户，无法注销！");
				return result;
			}
		}
		String getUsername = getPlayer.getUserName();
		redisTemplate.delete(id);
		redisTemplate.opsForHash().delete(CommonValue.REDIS_USERNAME_ID_TABLE_NAME, getUsername);
		redisTemplate.opsForZSet().remove(CommonValue.REDIS_RANK_TABLE_NAME, id);
		playerDAO.delete(id);
		result.setResultSuccess("注销用户完成！", null);
		sendNotifyMail(getPlayer.getEmail(), "宫子恰布丁-用户注销", "您的用户：" + getPlayer.getNickname() + " 已经成功注销！");
		return result;
	}

	@Override
	public Result<Player> update(Player player) {
		Result<Player> result = new Result<>();
		if (player == null) {
			result.setResultFailed("数据体不能为空！");
			return result;
		}
		Player getPlayer = (Player) redisTemplate.opsForValue().get(player.getId());
		if (getPlayer == null) {
			try {
				getPlayer = playerDAO.findById(player.getId());
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (getPlayer == null) {
				result.setResultFailed("找不到玩家！");
				return result;
			}
		}
		if (StringUtils.isEmpty(player.getNickname())) {
			player.setNickname(getPlayer.getNickname());
		}
		if (StringUtils.isEmpty(player.getAvatar())) {
			player.setAvatar(getPlayer.getAvatar());
		} else {
			String originAvatar = getPlayer.getAvatar();
		}
		if (player.getHighScore() == null) {
			player.setHighScore(getPlayer.getHighScore());
		} else {
			// 重新写入Redis排名表信息
			redisTemplate.opsForZSet().remove(CommonValue.REDIS_RANK_TABLE_NAME, player.getId());
			redisTemplate.opsForZSet().add(CommonValue.REDIS_RANK_TABLE_NAME, player.getId(), player.getHighScore());
		}
		if (StringUtils.isEmpty(player.getPwd())) {
			player.setPwd(getPlayer.getPwd());
		} else if (player.getPwd().length() < 8) {
			result.setResultFailed("修改密码长度不能小于8！");
			return result;
		} else {
			player.setPwd(PwdUtils.encodePwd(player.getPwd()));
		}
		if (StringUtils.isEmpty(player.getEmail())) {
			player.setEmail(getPlayer.getEmail());
		} else if (!player.getEmail().contains("@")) {
			result.setResultFailed("邮箱格式不正确！");
			return result;
		}
		if (StringUtils.isEmpty(player.getGameData())) {
			player.setGameData(getPlayer.getGameData());
		}
		player.setGmtCreated(getPlayer.getGmtCreated());
		player.setGmtModified(LocalDateTime.now());
		redisTemplate.opsForValue().set(player.getId(), player);
		playerDAO.update(player);
		result.setResultSuccess("修改信息成功！", player);
		return result;
	}

	@Override
	public Result<List<RankInfo>> getTotalRank() {
		Result<List<RankInfo>> result = new Result<>();
		List<RankInfo> rankResult = new ArrayList<>();
		Set<Object> userIds = redisTemplate.opsForZSet().reverseRange(CommonValue.REDIS_RANK_TABLE_NAME, 0, 9);
		if (userIds.size() == 0) {
			List<RankInfo> getRank = null;
			try {
				getRank = playerDAO.findByHighScoreInTen();
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (getRank == null) {
				result.setResultFailed("查询失败！");
				return result;
			} else {
				for (RankInfo rank : getRank) {
					rankResult.add(rank);
					redisTemplate.opsForZSet().add(CommonValue.REDIS_RANK_TABLE_NAME, rank.getUserId(), rank.getHighScore());
				}
			}
		} else {
			long order = 1;
			for (Object eachUserIdObject : userIds) {
				int eachUserId = (int) eachUserIdObject;
				//判断缓存排名表每个用户是否有效
				Player getPlayer = (Player) redisTemplate.opsForValue().get(eachUserId);
				if (getPlayer == null) {
					try {
						getPlayer = playerDAO.findById(eachUserId);
					} catch (Exception e) {
						e.printStackTrace();
					}
					if (getPlayer == null) {
						redisTemplate.opsForZSet().remove(CommonValue.REDIS_RANK_TABLE_NAME, eachUserId);
						continue;
					}
				}
				RankInfo info = new RankInfo();
				info.setUserId(getPlayer.getId());
				info.setNickname(getPlayer.getNickname());
				info.setAvatar(getPlayer.getAvatar());
				info.setHighScore(getPlayer.getHighScore());
				info.setSequence(order);
				rankResult.add(info);
				order++;
			}
		}
		result.setResultSuccess("获取排名成功！", rankResult);
		return result;
	}

	@Override
	public Result<RankInfo> getPlayerRank(int id) {
		Result<RankInfo> result = new Result<>();
		Long rank = redisTemplate.opsForZSet().reverseRank(CommonValue.REDIS_RANK_TABLE_NAME, id);
		if (rank == null) {
			RankInfo info = null;
			try {
				info = playerDAO.findUserRankByUserId(id);
			} catch (Exception e) {
				// none
			}
			if (info == null) {
				result.setResultFailed("查询失败！");
				return result;
			} else {
				redisTemplate.opsForZSet().add(CommonValue.REDIS_RANK_TABLE_NAME, info.getUserId(), info.getHighScore());
				result.setResultSuccess("查询成功！", info);
				return result;
			}
		} else {
			rank++;
		}
		double score = redisTemplate.opsForZSet().score(CommonValue.REDIS_RANK_TABLE_NAME, id);
		RankInfo rankInfo = new RankInfo();
		rankInfo.setUserId(id);
		rankInfo.setHighScore((int) score);
		rankInfo.setSequence(rank);
		result.setResultSuccess("查询成功！", rankInfo);
		return result;
	}

}