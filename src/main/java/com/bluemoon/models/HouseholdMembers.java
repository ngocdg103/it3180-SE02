package com.bluemoon.models;

import java.util.List;
import java.util.Optional;

/** Result of ResidentService.getHousehold: members of one household, head first. */
public record HouseholdMembers(String maHo, List<Resident> members) {
    public HouseholdMembers { members = List.copyOf(members); }

    public int memberCount() { return members.size(); }

    public Optional<Resident> head() { return members.stream().filter(Resident::isHead).findFirst(); }
}
