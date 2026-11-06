package com.lavagenesis.client;

import com.lavagenesis.eruption.client.EruptionClient;
import com.lavagenesis.experience.client.ExperienceClient;
import com.lavagenesis.form.client.FormClient;
import com.lavagenesis.land.client.LandClient;
import com.lavagenesis.vent.client.VentClient;
import net.fabricmc.api.ClientModInitializer;

public class LavaGenesisClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		LandClient.init();
		VentClient.init();
		FormClient.init();
		EruptionClient.init();
		ExperienceClient.init();
	}
}
