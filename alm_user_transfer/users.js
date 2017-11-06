var _ = require('underscore');
var Octane = require('hpe-alm-octane-js-rest-sdk');
4
var octane = new Octane({
	protocol: "http",
	host: "myd-vm18992.hpeswlab.net",
	port: 25675,
	shared_space_id: "1001",
	workspace_id: "1003"
});


var Client = require('node-rest-client').Client;

// configure basic http auth for every request
var options_auth = {user: "gil", password: "gil"};
var client = new Client(options_auth);
client.post("http://myd-vm24797.hpeswlab.net:8080/qcbin/api/authentication/sign-in", {}, function(data, response) {
	client.get("http://myd-vm24797.hpeswlab.net:8080/qcbin/rest/domains/DEFAULT/projects/Mediolanum/customization/users", {
		headers: {
			"Accept": "application/json",
			"Cookie": response.headers["set-cookie"]
		}
	}, function(data) {

		octane.authenticate({
			clientId: "mediolanum_51e0v18d2kn15um93ep6mx49m",
			clientSecret: ")a530cc23beba3fedP"
		}, function(err) {
			if (err) {
				console.log('Error - %s', err.message)
				return
			}

			octane.userRoles.getAll({}, function(err, defects) {
				if (err) {
					console.log('Error - %s', err.message)
					return
				}

				var memberRoleId = _.find(defects, function(defect) {
					return defect.logical_name === 'role.workspace.team.member';
				}).id;

				data.users.forEach (function (user) {
					var originalEmail = user.email;
					var newEmail = originalEmail ? originalEmail : (user.Name + '@default');
					var newUser = {
						first_name: user.Name,
						email: newEmail,
						phone1: user.phone,
						last_name: user.Name,
						roles: [{
							type: 'user_role',
							id: memberRoleId
						}]
					};

					octane.workspaceUsers.create(newUser, function (err, user) {
						console.log(user)
					});
				});

			});
		});
	})
});
